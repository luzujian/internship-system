package com.internship.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.mapper.SmartImportWorkspaceMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SmartImportConversationStoreTest {
    @TempDir Path directory;
    final ObjectMapper json=new ObjectMapper();
    final AgentTypes.Actor admin=new AgentTypes.Actor("admin1",1L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
    final String session=UUID.randomUUID().toString();
    final MemoryRows rows=new MemoryRows();
    final RedisTemplate<String,String> redis=mock(RedisTemplate.class);
    final ValueOperations<String,String> values=mock(ValueOperations.class);
    final Map<String,String> legacy=new HashMap<>();
    final List<SmartImportConversationStore> stores=new ArrayList<>();
    @BeforeEach void setup(){when(redis.opsForValue()).thenReturn(values);when(values.get(anyString())).thenAnswer(c->legacy.get(c.getArgument(0)));}
    @AfterEach void stop(){stores.forEach(SmartImportConversationStore::close);}
    SmartImportConversationStore store(){var store=new SmartImportConversationStore(redis,json,rows,new PrivateImportAttachmentStorage(directory.resolve("private"),directory.resolve("public")));stores.add(store);return store;}
    List<SmartWorkbookReader.SourceSheet> source(){return List.of(new SmartWorkbookReader.SourceSheet("0:0","名单.xlsx","名单",List.of(new SmartWorkbookReader.SourceRow(3,List.of("00123","测试甲"),Set.of(3,1)))));}

    @Test void restartedStoreRestoresSourcePlanPreviewAndReviewedActionWithoutRedis() {
        var first=store();var state=first.create(admin,session,source());
        state.setOptions(new SmartImportService.Options("student",null,null,Map.of("班级","实际班级"),null,null,null));
        state.setPreview(Map.of("actionId","preview-one","ready",1));state.setReviewedActionId("preview-one");state.setPhase("WAITING_CONFIRM");first.save(state);
        first.close();reset(redis);
        var recovered=store().owned(admin,state.getId(),session);
        assertEquals("00123",recovered.getSource().getFirst().rows().getFirst().cells().getFirst());
        assertEquals("实际班级",recovered.getOptions().defaults().get("班级"));assertEquals("preview-one",recovered.getReviewedActionId());
        assertEquals("WAITING_CONFIRM",recovered.getPhase());assertEquals(state.getSourceSha256(),recovered.getSourceSha256());
        assertFalse(rows.data.get(state.getId()).getStateJson().contains("00123"));verifyNoInteractions(redis);
    }
    @Test void bothAccountAndSessionAreRequiredBeforeDecodingPrivateSource() {
        var store=store();var state=store.create(admin,session,source());rows.data.get(state.getId()).setSourceJson("corrupt-private-data");
        var other=new AgentTypes.Actor("admin2",2L,"ROLE_ADMIN","其他管理员",null,null,null,false,Set.of());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->store.owned(other,state.getId(),session)).getCode());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->store.owned(admin,state.getId(),UUID.randomUUID().toString())).getCode());
        assertEquals("IMPORT_SOURCE_CORRUPTED",assertThrows(AgentException.class,()->store.owned(admin,state.getId(),session)).getCode());
    }
    @Test void simultaneousLeaseAndStaleSnapshotCannotChangeNewerReviewedPreview() {
        var store=store();var initial=store.create(admin,session,source());
        var old=store.owned(admin,initial.getId(),session);var current=store.owned(admin,initial.getId(),session);
        current.setReviewedActionId("new-preview");store.save(current);old.setReviewedActionId("old-preview");
        assertEquals("IMPORT_STATE_CONFLICT",assertThrows(AgentException.class,()->store.save(old)).getCode());
        assertEquals("new-preview",store.owned(admin,initial.getId(),session).getReviewedActionId());
        try(var lease=store.lock(initial.getId(),"turn-one")){
            assertEquals("IMPORT_BUSY",assertThrows(AgentException.class,()->store.lock(initial.getId(),"turn-two")).getCode());
            var held=store.owned(admin,initial.getId(),session);store.bindLease(held,lease);held.setActiveTurnId("turn-one");store.save(held);
        }
        assertFalse(store.locked(initial.getId()));
    }
    @Test void cancelIsDurableAndAnExpiredWorkerCannotOverwriteOrReleaseTheNextTurn() {
        var store=store();var state=store.create(admin,session,source());var oldLease=store.lock(state.getId(),"old-turn");
        var old=store.owned(admin,state.getId(),session);store.bindLease(old,oldLease);old.setActiveTurnId("old-turn");old.setPhase("WORKING");store.save(old);
        store.cancel(state.getId(),"old-turn");assertTrue(store().cancelled(state.getId(),"old-turn"));
        assertThrows(AgentException.class,oldLease::assertActive);assertThrows(AgentException.class,()->store.save(old));
        try(var next=store.lock(state.getId(),"next-turn")){
            var newer=store.owned(admin,state.getId(),session);store.bindLease(newer,next);newer.setActiveTurnId("next-turn");newer.setReviewedActionId("new");store.save(newer);
            oldLease.close();assertTrue(store.locked(state.getId()));assertEquals("new",store.owned(admin,state.getId(),session).getReviewedActionId());
        }
    }
    @Test void expiredLeaseNeedsANewTokenAndOldOwnerCannotWrite() {
        var store=store();var state=store.create(admin,session,source());var old=store.lock(state.getId(),"old");
        var stale=store.owned(admin,state.getId(),session);store.bindLease(stale,old);rows.data.get(state.getId()).setLeaseUntil(0);
        try(var next=store.lock(state.getId(),"new")){assertThrows(AgentException.class,old::assertActive);assertThrows(AgentException.class,()->store.save(stale));old.close();next.assertActive();}
    }
    @Test void legacyWorkspaceMigratesOnceWithoutInventingOriginalFiles() throws Exception {
        var state=new SmartImportConversationStore.State();state.setId(UUID.randomUUID().toString());state.setOwnerKey(admin.ownerKey());state.setSessionId(session);state.setSource(source());
        state.setPreview(Map.of("actionId","old-reviewed"));state.setReviewedActionId("old-reviewed");legacy.put("agent:smart-import:"+state.getId(),json.writeValueAsString(state));
        var migrated=store().owned(admin,state.getId(),session);assertTrue(migrated.isLegacySourceOnly());assertTrue(migrated.getAttachments().isEmpty());assertEquals("old-reviewed",migrated.getReviewedActionId());
        legacy.clear();reset(redis);assertEquals("00123",store().owned(admin,state.getId(),session).getSource().getFirst().rows().getFirst().cells().getFirst());verifyNoInteractions(redis);
    }
    @Test void changedOriginalRowsAndMissingDurableTableFailClosed() {
        var store=store();var state=store.create(admin,session,source());state.setSource(List.of());assertThrows(AgentException.class,()->store.save(state));
        var missing=mock(SmartImportWorkspaceMapper.class);when(missing.find(anyString())).thenThrow(new IllegalStateException("missing migration"));
        var unavailable=new SmartImportConversationStore(redis,json,missing,mock(PrivateImportAttachmentStorage.class));stores.add(unavailable);
        assertEquals("IMPORT_CONTEXT_UNAVAILABLE",assertThrows(AgentException.class,()->unavailable.owned(admin,UUID.randomUUID().toString(),session)).getCode());
        verify(redis,never()).opsForValue();
    }
    /** A shared fake durable repository; no model, SQL server, or production data is accessed. */
    final class MemoryRows implements SmartImportWorkspaceMapper {
        final Map<String,SmartImportWorkspaceRecord> data=new HashMap<>();
        SmartImportWorkspaceRecord copy(SmartImportWorkspaceRecord r){return json.convertValue(r,SmartImportWorkspaceRecord.class);}
        public synchronized int insert(SmartImportWorkspaceRecord row){if(data.containsKey(row.getId()))throw new DuplicateKeyException("duplicate");data.put(row.getId(),copy(row));return 1;}
        public synchronized SmartImportWorkspaceRecord find(String id){return data.containsKey(id)?copy(data.get(id)):null;}
        public synchronized int save(String id,String owner,String session,long version,String snapshot,String token,long now){
            var r=data.get(id);if(r==null || !owner.equals(r.getOwnerKey()) || !session.equals(r.getSessionId()) || version!=r.getVersion())return 0;
            boolean allowed=token==null ? r.getLeaseToken()==null || r.getLeaseUntil()<=now : token.equals(r.getLeaseToken()) && r.getLeaseUntil()>now && !Objects.equals(r.getLeaseTurnId(),r.getCancelledTurnId());
            if(!allowed)return 0;r.setStateJson(snapshot);r.setVersion(version+1);r.setUpdatedAt(now);return 1;
        }
        public synchronized int claim(String id,String token,String turn,long now,long until){var r=data.get(id);if(r==null || r.getLeaseToken()!=null && r.getLeaseUntil()>now || turn.equals(r.getCancelledTurnId()))return 0;r.setLeaseToken(token);r.setLeaseTurnId(turn);r.setLeaseUntil(until);return 1;}
        public synchronized int renew(String id,String token,long now,long until){var r=data.get(id);if(r==null || !token.equals(r.getLeaseToken()) || r.getLeaseUntil()<=now || Objects.equals(r.getLeaseTurnId(),r.getCancelledTurnId()))return 0;r.setLeaseUntil(until);return 1;}
        public synchronized int release(String id,String token){var r=data.get(id);if(r==null || !token.equals(r.getLeaseToken()))return 0;r.setLeaseToken(null);r.setLeaseTurnId(null);r.setLeaseUntil(0);return 1;}
        public synchronized int cancel(String id,String turn,long now){var r=data.get(id);if(r==null || r.getLeaseToken()!=null && r.getLeaseUntil()>now && !turn.equals(r.getLeaseTurnId()))return 0;r.setCancelledTurnId(turn);r.setLeaseToken(null);r.setLeaseTurnId(null);r.setLeaseUntil(0);r.setVersion(r.getVersion()+1);r.setUpdatedAt(now);return 1;}
    }
}
