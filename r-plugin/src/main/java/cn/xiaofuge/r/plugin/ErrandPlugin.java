package cn.xiaofuge.r.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 跑腿代办管家插件：把 errand-service REST API 注册为 DSH Agent 工具 */
public class ErrandPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "errand-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public ErrandPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new ServiceListTool(),
                new RunnerListTool(),
                new PlaceOrderTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("errand-capabilities", 20, """
                ## AI 跑腿代办管家（同城跑腿平台 · 2026-09-25）
                - 查服务 → service_list（5 项服务单价与时效：文档取送29/蛋糕配送39/代购跑腿35/排队代办59/跨城急送199；
                  会员月卡 29 元每月 3 单免跑腿费；暴雨台风天加价 30%）
                - 查跑男 → runner_list（3 位跑男特长/评分/在单量）
                - 下单 → place_order（customer/phone/service/pickup/dropoff 必填，runnerId 可选默认平台派单，note 备注；
                  必须先复述服务、单价、取件地址、送达地址、时效请用户确认后才能调用；成功报单号）
                - 订单查询 → order_info（orderId：E7001 格式；服务/取送地址/跑男/金额/状态）
                - 问运营 → stats（总单量/待接单/配送中/已送达/营收与预计营收/分服务分跑男分布/运营建议）
                - 回答要求：
                  1) 下单前必须复述要素（服务/单价/取件地址/送达地址/时效）请用户确认
                  2) 下单结果必报单号、跑男与时效
                  3) 提醒：贵重物品（证件/现金/珠宝）不接单；代购垫付需用户先转垫付款；配送超时 30 分钟以上退一半跑腿费
                  4) 价格与优惠只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: errand tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("ERRAND_APP_BASE_URL", "http://127.0.0.1:18116")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class ServiceListTool extends AbstractTool {
        @Override public String name() { return "service_list"; }
        @Override public String description() {
            return "跑腿服务价目表：5 项服务的单价与时效（文档/蛋糕/代购/排队/跨城），含月卡与恶劣天气加价规则。"
                    + "报价、下单前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/services", args));
        }
    }

    private class RunnerListTool extends AbstractTool {
        @Override public String name() { return "runner_list"; }
        @Override public String description() {
            return "跑男列表：姓名/特长/评分/在单量。用户指定跑男、问谁送得快时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/runners", args));
        }
    }

    private class PlaceOrderTool extends AbstractTool {
        @Override public String name() { return "place_order"; }
        @Override public String description() {
            return "跑腿下单：customer（下单人）/phone（联系电话）/service（服务项目）/pickup（取件地址）/dropoff（送达地址）必填，"
                    + "runnerId（跑男 R01-R03）可选默认平台派单，note（备注）可选。必须先复述服务、单价、取送地址、时效经用户确认后才能调用。"
                    + "成功返回单号。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("下单人姓名"))
                    .prop("phone", stringSchema("联系电话"))
                    .prop("service", stringSchema("服务：文档取送 / 蛋糕配送 / 代购跑腿 / 排队代办 / 跨城急送"))
                    .prop("pickup", stringSchema("取件地址"))
                    .prop("dropoff", stringSchema("送达地址"))
                    .prop("runnerId", stringSchema("跑男编号 R01-R03，可选，默认平台派单"))
                    .prop("note", stringSchema("备注，如：加急 / 防抖 / 取号时间"))
                    .required("customer", "phone", "service", "pickup", "dropoff")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"service\":\"" + json(str(args, "service"))
                    + "\",\"pickup\":\"" + json(str(args, "pickup"))
                    + "\",\"dropoff\":\"" + json(str(args, "dropoff"))
                    + "\",\"runnerId\":\"" + json(str(args, "runnerId"))
                    + "\",\"note\":\"" + json(str(args, "note")) + "\"}";
            return ok(post("/api/order", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（E7001 格式）。返回服务/取送地址/跑男/金额/状态（待接单、配送中、已送达、已取消）。"
                    + "何时必须调用：用户问订单、问送到哪了。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 E7001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order/info?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/待接单/配送中/已送达/营收与预计营收/分服务分跑男分布/运营建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
