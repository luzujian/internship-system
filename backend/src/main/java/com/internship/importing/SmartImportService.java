package com.internship.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.internship.importing.SmartImportValidation.*;

@Service
@RequiredArgsConstructor
public class SmartImportService {
    static final Set<String> SHARED_FIELDS = Set.of("专业","班级","年级","教师类型","院系","系","负责教师",
            "院系名称","系名称","类别名称","企业地址","所在城市","目标群体","目标值","状态","企业名称");
    private final SmartWorkbookReader reader;
    private final SmartImportRecognition recognition;
    private final SmartImportCatalog catalog;
    private final SmartImportExecutor executor;
    private final AgentActionService actions;
    private final AgentIdentityService identity;
    private final ObjectMapper json;
    private com.internship.service.AdminPermissionService adminPermissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAdminPermissions(com.internship.service.AdminPermissionService permissions) { this.adminPermissions = permissions; }
    public record Options(String type,String message,List<SmartImportRecognition.Layout> sheets,Map<String,String> defaults,
                          Map<String,Map<String,String>> rowOverrides,Map<String,String> referenceOverrides,Set<String> excludedRows) {}
    public record ConfirmRequest(String actionId,boolean confirmed,boolean allowPartial) {}

    public Map<String,Object> preview(String authorization,List<MultipartFile> files,String optionsJson) {
        Actor actor = liveActor(authorization); requireAdmin(actor);
        Options options;
        try {
            if (optionsJson != null && optionsJson.length() > 1_500_000) throw new IllegalArgumentException();
            options = optionsJson == null || optionsJson.isBlank() ? new Options(null,null,null,null,null,null,null) : json.readValue(optionsJson,Options.class);
            if (options == null || Objects.toString(options.message(),"").length() > 4000) throw new IllegalArgumentException();
        } catch(Exception e) { throw SmartWorkbookReader.invalid("导入参数无效，请重新选择文件。"); }
        return previewSource(authorization,reader.read(files),options);
    }
    /** The conversation retains parsed source data on the server; every revision uses the same validator. */
    Map<String,Object> previewSource(String authorization,List<SmartWorkbookReader.SourceSheet> source,Options options) {
        Actor actor = liveActor(authorization); requireAdmin(actor);
        if (options.type() != null && !options.type().isBlank()) requireLivePermission(actor,SmartImportType.parse(options.type()).permission);
        var detected = recognition.recognize(actor,source,options.type(),options.message(),options.sheets());
        SmartImportType type = detected.type(); requireLivePermission(actor,type.permission);
        Map<String,String> defaults = new LinkedHashMap<>();
        if (options.defaults() != null) defaults.putAll(options.defaults());
        defaults.putAll(detected.defaults());
        defaults.entrySet().removeIf(e -> !SHARED_FIELDS.contains(e.getKey()) || !type.fieldNames().contains(e.getKey())
                || ("企业名称".equals(e.getKey()) && type != SmartImportType.POSITION_CATEGORY));
        validateValues(type,defaults);
        if (options.rowOverrides() != null) {
            if (options.rowOverrides().size() > SmartWorkbookReader.MAX_ROWS) throw SmartWorkbookReader.invalid("修改行数过多。");
            options.rowOverrides().values().forEach(v -> validateValues(type,v));
        }
        List<InputRow> input = new ArrayList<>(); List<CheckedRow> excluded = new ArrayList<>(); List<String> warnings = new ArrayList<>(detected.warnings());
        Map<String,List<Issue>> sourceIssues = new HashMap<>();
        List<Map<String,Object>> layouts = new ArrayList<>();
        for (var sheet : source) {
            var layout = detected.layouts().stream().filter(l -> sheet.id().equals(l.sheetId())).findFirst().orElseThrow();
            var header = sheet.rows().stream().filter(r -> r.number() == layout.headerRow()).findFirst().orElseThrow();
            List<String> ignored = new ArrayList<>();
            for (int c = 0; c < header.cells().size(); c++) if (!layout.columns().containsValue(c + 1) && !header.cells().get(c).isBlank()) ignored.add(header.cells().get(c));
            if (!ignored.isEmpty()) warnings.add(sheet.fileName() + " / " + sheet.name() + " 未导入的列：" + String.join("、",ignored));
            layouts.add(Map.of("sheetId",sheet.id(),"fileName",sheet.fileName(),"name",sheet.name(),"headerRow",layout.headerRow(),
                    "columns",layout.columns(),"headers",header.cells(),"headerChoices",sheet.rows().stream().limit(50)
                    .map(r -> Map.of("value",r.number(),"cells",r.cells().stream().map(c -> c.substring(0,Math.min(c.length(),120))).toList(),"label","第" + r.number() + "行：" + String.join(" / ",r.cells().stream().filter(c -> !c.isBlank()).limit(5).map(c -> c.substring(0,Math.min(c.length(),24))).toList()))).toList()));
            for (var row : sheet.rows()) {
                if (row.number() <= layout.headerRow()) continue;
                if (row.cells().equals(header.cells())) continue;
                Map<String,String> values = new LinkedHashMap<>();
                for (var field : type.fields) {
                    Integer column = layout.columns().get(field.name());
                    String value = column == null || column > row.cells().size() ? "" : row.cells().get(column - 1);
                    if (value.isBlank()) value = Objects.toString(defaults.get(field.name()),"");
                    if (options.referenceOverrides() != null) value = options.referenceOverrides().getOrDefault(field.name() + "|" + value,value);
                    values.put(field.name(),value);
                }
                String id = sheet.id() + ":" + row.number();
                if (options.rowOverrides() != null && options.rowOverrides().containsKey(id)) values.putAll(options.rowOverrides().get(id));
                for (var column : layout.columns().entrySet()) if (row.uncertainColumns().contains(column.getValue())
                        && (options.rowOverrides() == null || !options.rowOverrides().getOrDefault(id,Map.of()).containsKey(column.getKey())))
                    sourceIssues.computeIfAbsent(id,key -> new ArrayList<>()).add(new Issue(column.getKey(),"原表“" + column.getKey() + "”含公式、单元格错误或超出数值精度，请核对后输入文本值。"));
                validateValues(type,values);
                var item = new InputRow(id,sheet.fileName(),sheet.name(),row.number(),values);
                if (options.excludedRows() != null && options.excludedRows().contains(id))
                    excluded.add(new CheckedRow(id,item.file(),item.sheet(),item.row(),values,"SKIPPED",List.of(),List.of("已由你选择不导入。")));
                else input.add(item);
            }
        }
        if (input.size() + excluded.size() > SmartWorkbookReader.MAX_ROWS) throw SmartWorkbookReader.invalid("本次最多导入5000条，请拆分文件。");
        var currentData = catalog.load(type,input.stream().map(InputRow::values).toList());
        var checked = new ArrayList<>(SmartImportValidation.check(type,input,currentData)); checked.addAll(excluded);
        for (int i = 0; i < checked.size(); i++) {
            var row = checked.get(i);
            if (sourceIssues.containsKey(row.id()) && (options.excludedRows() == null || !options.excludedRows().contains(row.id()))) {
                var issues = new ArrayList<>(row.issues()); issues.addAll(sourceIssues.get(row.id()));
                checked.set(i,new CheckedRow(row.id(),row.file(),row.sheet(),row.row(),row.values(),"BLOCKED",issues,row.notes()));
            }
        }
        var ready = checked.stream().filter(r -> "READY".equals(r.status())).map(r -> new InputRow(r.id(),r.file(),r.sheet(),r.row(),r.values())).toList();
        Map<String,Object> summary = Map.of("total",checked.size(),"ready",ready.size(),"skipped",count(checked,"SKIPPED"),"blocked",count(checked,"BLOCKED"));
        Actor current = liveActor(authorization);
        if (!actor.ownerKey().equals(current.ownerKey())) throw AgentIdentityService.forbidden();
        requireLivePermission(current,type.permission); requireAdditionalPermissions(current,type,ready,currentData);
        String actionId = null; long expiresAt = 0;
        if (!ready.isEmpty()) {
            var action = actions.preview(current,"SMART_IMPORT",type.key,Map.of("type",type.key,"rows",ready,"summary",summary),null);
            actionId = action.id(); expiresAt = action.expiresAt();
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("type",type.key); result.put("label",type.label); result.put("fields",type.fields); result.put("sheets",layouts);
        result.put("defaults",defaults); result.put("rows",checked); result.put("summary",summary); result.put("references",currentData.options(type));
        result.put("warnings",warnings); result.put("questions",checked.stream().flatMap(r -> r.issues().stream()).map(Issue::message).distinct().limit(80).toList());
        result.put("actionId",actionId); result.put("expiresAt",expiresAt);
        return result;
    }
    public Map<String,Object> confirm(String authorization,ConfirmRequest request) {
        Actor actor = liveActor(authorization); requireAdmin(actor);
        if (request == null || !request.confirmed()) throw new AgentException("CONFIRM_REQUIRED","请先核对导入预览并明确确认导入。",400);
        var action = actions.get(actor,request.actionId());
        if (!"SMART_IMPORT".equals(action.actionType())) throw new AgentException("ACTION_MISMATCH","操作类型不正确，请重新打开导入预览。",409);
        var parameters = action.parameters(); SmartImportType type = SmartImportType.parse(Objects.toString(parameters.get("type"),""));
        requireLivePermission(actor,type.permission);
        if ("SUCCEEDED".equals(action.status())) return action.receipt();
        Map<String,Object> summary = json.convertValue(parameters.get("summary"),new TypeReference<>(){});
        if (((Number)summary.getOrDefault("blocked",0)).intValue() > 0 && !request.allowPartial())
            throw new AgentException("IMPORT_ISSUES_PENDING","仍有问题行，请修正，或明确选择仅导入校验通过的数据。",400);
        return actions.execute(actor,request.actionId(),"SMART_IMPORT",parameters,() -> {
            Actor current = liveActor(authorization);
            if (!actor.ownerKey().equals(current.ownerKey())) throw AgentIdentityService.forbidden();
            requireLivePermission(current,type.permission);
            List<InputRow> rows = json.convertValue(parameters.get("rows"),new TypeReference<>(){});
            var data = catalog.load(type,rows.stream().map(InputRow::values).toList());
            requireAdditionalPermissions(current,type,rows,data);
            var checked = SmartImportValidation.check(type,rows,data);
            for (int i = 0; i < rows.size(); i++) if (!"READY".equals(checked.get(i).status()) || !rows.get(i).values().equals(checked.get(i).values()))
                throw new AgentException("IMPORT_CHANGED","预览后系统数据已变化，请重新检查；本次未新增数据。",409);
            var receipt = executor.execute(current,type,rows,data);
            receipt.put("type",type.key); receipt.put("label",type.label); receipt.put("skippedCount",summary.get("skipped"));
            receipt.put("blockedCount",summary.get("blocked")); receipt.put("importedRows",rows.size());
            receipt.put("completedAt",System.currentTimeMillis());
            return receipt;
        });
    }
    static void requireAdmin(Actor actor) {
        AgentIdentityService.requireAdmin(actor);
        if (actor.readOnly()) throw new AgentException("READ_ONLY_FORBIDDEN","只读身份不能导入数据。",403);
    }
    static void requirePermission(Actor actor,String permission) {
        requireAdmin(actor);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!actor.permissions().contains(permission) && (auth == null || auth.getAuthorities().stream().noneMatch(a -> permission.equals(a.getAuthority()))))
            throw new AgentException("IMPORT_FORBIDDEN","当前账号缺少导入所需权限：" + permission,403);
    }
    private void requireAdditionalPermissions(Actor actor,SmartImportType type,List<InputRow> rows,SmartImportCatalog.Snapshot data) {
        if (type == SmartImportType.DEPARTMENT_MAJOR && rows.stream().anyMatch(r -> data.departments().stream().noneMatch(d -> text(r.values(),"院系名称").equals(d.getName())))) requireLivePermission(actor,"department:add");
        if (type == SmartImportType.POSITION_CATEGORY && rows.stream().anyMatch(r -> !text(r.values(),"岗位名称").isBlank())) requireLivePermission(actor,"recruitment:add");
    }
    private Actor liveActor(String authorization) {
        Actor actor = identity.resolve(authorization);
        if (adminPermissions == null || !"ROLE_ADMIN".equals(actor.role())) return actor;
        var granted = adminPermissions.getRolePermissionsByCode(actor.role()); Set<String> codes = new HashSet<>();
        if (granted != null) granted.forEach(p -> codes.add(p.getPermissionCode()));
        return new Actor(actor.ownerKey(),actor.id(),actor.role(),actor.name(),actor.teacherType(),actor.departmentId(),actor.divisionId(),actor.readOnly(),codes);
    }
    private void requireLivePermission(Actor actor,String permission) {
        if (adminPermissions != null && !actor.permissions().contains(permission)) throw new AgentException("IMPORT_FORBIDDEN","当前账号缺少导入所需权限：" + permission,403);
        requirePermission(actor,permission);
    }
    private static long count(List<CheckedRow> rows,String status) { return rows.stream().filter(r -> status.equals(r.status())).count(); }
    private static void validateValues(SmartImportType type,Map<String,String> values) {
        if (values == null || values.size() > type.fields.size()) throw SmartWorkbookReader.invalid("字段内容无效。");
        for (var e : values.entrySet()) if (!type.fieldNames().contains(e.getKey()) || e.getValue() == null || e.getValue().length() > 16000)
            throw SmartWorkbookReader.invalid("字段名称或内容无效。");
    }
}
