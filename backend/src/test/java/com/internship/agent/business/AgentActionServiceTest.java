package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.AgentActionMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.internship.agent.business.AgentTypes.Actor;

/** Isolated in-memory mapper and transaction stubs; never connects to any database. */
class AgentActionServiceTest {
    AgentActionMapper mapper;AgentActionService service;
    final Map<String,AgentActionService.ActionRecord> rows=new ConcurrentHashMap<>();
    final Actor actor=new Actor("ROLE_STUDENT:1:false:",1L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
    @BeforeEach void setup(){
        mapper=mock(AgentActionMapper.class);PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        doAnswer(c->{AgentActionService.ActionRecord row=c.getArgument(0);rows.put(row.getId(),row);return null;}).when(mapper).insert(any());
        when(mapper.find(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(mapper.lock(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(mapper.claim(anyString(),anyLong())).thenAnswer(c->{synchronized(rows){var row=rows.get(c.getArgument(0));if(!"READY".equals(row.getStatus()))return 0;row.setStatus("RUNNING");return 1;}});
        when(mapper.finish(anyString(),anyString(),nullable(String.class),nullable(String.class),nullable(String.class),anyLong())).thenAnswer(c->{
            var row=rows.get(c.getArgument(0));row.setStatus(c.getArgument(1));row.setReceipt(c.getArgument(2));row.setErrorCode(c.getArgument(3));row.setErrorMessage(c.getArgument(4));return 1;
        });
        service=new AgentActionService(mapper,new ObjectMapper(),manager);
    }
    AgentActionService.ActionView preview(){return service.preview(actor,"JOB_WITHDRAW","application:9",Map.of("applicationId","9"),"v1");}
    @Test void completedWithdrawalKeepsReceiptWhenBusinessObjectIsGone(){
        var action=preview();AtomicInteger writes=new AtomicInteger();
        var first=service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->{writes.incrementAndGet();return Map.of("applicationId","9","status","withdrawn");});
        var again=service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->{fail("must not mutate twice");return Map.of();});
        assertEquals(first,again);assertEquals(1,writes.get());assertEquals("withdrawn",service.get(actor,action.id()).receipt().get("status"));
    }
    @Test void concurrentConfirmationCannotRepeatMutation()throws Exception{
        var action=preview();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger writes=new AtomicInteger();
        try(ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor()){
            Future<Map<String,Object>> first=pool.submit(()->service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->{
                entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}writes.incrementAndGet();return Map.of("status","withdrawn");
            }));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals("ACTION_PENDING",assertThrows(AgentException.class,()->service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->Map.of())).getCode());
            release.countDown();assertEquals("SUCCEEDED",first.get(2,TimeUnit.SECONDS).get("actionStatus"));assertEquals(1,writes.get());
        }
    }
    @Test void changedParametersAndExpiredPreviewNeverMutate(){
        var action=preview();
        assertEquals("ACTION_MISMATCH",assertThrows(AgentException.class,()->service.execute(actor,action.id(),"JOB_WITHDRAW",Map.of("applicationId","10"),()->Map.of())).getCode());
        rows.get(action.id()).setExpiresAt(0);
        assertEquals("PREVIEW_EXPIRED",assertThrows(AgentException.class,()->service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->Map.of())).getCode());
        verify(mapper,never()).claim(anyString(),anyLong());
    }
    @Test void ownershipCannotBeBorrowed(){
        var action=preview();Actor other=new Actor("ROLE_STUDENT:2:false:",2L,"ROLE_STUDENT","其他学生",null,null,null,false,Set.of());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.get(other,action.id())).getCode());
    }
    @Test void unknownResultCannotBeRetriedAutomatically(){
        var action=preview();rows.get(action.id()).setStatus("UNKNOWN");
        assertEquals("ACTION_UNKNOWN",assertThrows(AgentException.class,()->service.execute(actor,action.id(),"JOB_WITHDRAW",action.parameters(),()->Map.of())).getCode());
        verify(mapper,never()).claim(anyString(),anyLong());
    }
}
