package cn.xiaofuge.r.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 跑腿代办数据中心：服务项目/跑男/订单/统计 */
@Component
public class RStore {

    /** 服务：单价(元)/时效/说明 */
    static final Map<String, Object[]> SERVICES = new LinkedHashMap<>();
    static {
        SERVICES.put("文档取送", new Object[]{29.0, "2 小时内", "同城文件、合同、票据急送"});
        SERVICES.put("蛋糕配送", new Object[]{39.0, "1.5 小时内", "专业防抖箱，烛台不歪"});
        SERVICES.put("代购跑腿", new Object[]{35.0, "2 小时内", "药品/奶茶/急用品代买（垫付另计）"});
        SERVICES.put("排队代办", new Object[]{59.0, "按预约", "政务/银行/医院排队，半小时起步"});
        SERVICES.put("跨城急送", new Object[]{199.0, "当日达", "高铁捎带，跨城 300 公里内"});
    }

    /** 跑男：编号/姓名/特长/评分 */
    static final Map<String, Object[]> RUNNERS = new LinkedHashMap<>();
    static {
        RUNNERS.put("R01", new Object[]{"雷哥", "全城文件急送/夜单", 4.9});
        RUNNERS.put("R02", new Object[]{"小跑", "蛋糕/鲜花易碎品配送", 4.8});
        RUNNERS.put("R03", new Object[]{"阿捷", "排队代办/政务流程熟", 5.0});
    }

    public static class Order {
        public String id; public String customer; public String phone;
        public String pickup; public String dropoff; public String service; public String runner;
        public String note; public double total; public String status; // 待接单 / 配送中 / 已送达 / 已取消
    }

    public final List<Order> orders = new ArrayList<>();
    private int orderSeq = 7001;

    public RStore() { seed(); }

    private void seed() {
        orders.add(o("庞先生", "13800111111", "金融城写字楼 A 座", "高新区天晖路", "文档取送", "R01", "加急，下班前必达", "配送中"));
        orders.add(o("茅女士", "13800222222", "幸福里蛋糕店", "翡翠湾 3 栋", "蛋糕配送", "R02", "2 磅，防抖", "已送达"));
        orders.add(o("柯先生", "13800333333", "市政务中心", "", "排队代办", "R03", "周二上午取号", "待接单"));
    }

    private Order o(String customer, String phone, String pickup, String dropoff, String service, String runnerId, String note, String status) {
        Order x = new Order(); x.id = "E" + orderSeq++; x.customer = customer; x.phone = phone;
        x.pickup = pickup; x.dropoff = dropoff; x.service = service;
        x.runner = runnerId == null ? "待分配" : String.valueOf(RUNNERS.get(runnerId)[0]);
        x.note = note; x.total = (Double) SERVICES.get(service)[0]; x.status = status; return x;
    }

    /** 服务价目表 */
    public Map<String, Object> serviceList() {
        List<Map<String, Object>> list = new ArrayList<>();
        SERVICES.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("service", k); m.put("price", v[0]); m.put("eta", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("services", list);
        r.put("note", "会员月卡 ¥29/月，每月 3 单免跑腿费；暴雨/台风天加价 30%");
        return r;
    }

    /** 跑男列表 */
    public Map<String, Object> runnerList() {
        List<Map<String, Object>> list = RUNNERS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("skill", e.getValue()[1]); m.put("rating", e.getValue()[2]);
                    m.put("activeOrders", orders.stream().filter(o -> o.runner.equals(e.getValue()[0])
                            && ("待接单".equals(o.status) || "配送中".equals(o.status))).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("runners", list);
        return r;
    }

    /** 下单 */
    public synchronized Map<String, Object> place(String customer, String phone, String service, String pickup, String dropoff, String runnerId, String note) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供下单人姓名");
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供联系电话，方便跑男联系");
        Object[] s = SERVICES.get(service);
        if (s == null) return Map.of("ok", false, "msg", "服务 " + service + " 不在价目表，可选：" + String.join("/", SERVICES.keySet()));
        if (pickup == null || pickup.isBlank())
            return Map.of("ok", false, "msg", "请提供取件地址");
        if (dropoff == null || dropoff.isBlank())
            return Map.of("ok", false, "msg", "请提供送达地址");
        String rName;
        if (runnerId == null || runnerId.isBlank()) {
            rName = "待分配（平台派单）";
        } else {
            var rEntry = RUNNERS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(runnerId)).findFirst().orElse(null);
            if (rEntry == null) return Map.of("ok", false, "msg", "跑男 " + runnerId + " 不存在，可选：" + String.join("/", RUNNERS.keySet()));
            rName = String.valueOf(rEntry.getValue()[0]);
        }
        double price = (Double) s[0];
        Order x = new Order(); x.id = "E" + orderSeq++; x.customer = customer; x.phone = phone;
        x.pickup = pickup; x.dropoff = dropoff; x.service = service; x.runner = rName;
        x.note = note == null ? "" : note; x.total = price; x.status = "待接单";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", customer);
        r.put("service", service); r.put("pickup", pickup); r.put("dropoff", dropoff);
        r.put("runner", rName); r.put("eta", s[1]); r.put("total", price);
        r.put("msg", "下单成功！单号 " + x.id + "，" + service + "（¥" + price + "），取件 " + pickup + " → 送达 " + dropoff + "，跑男 " + rName + "，时效 " + s[1]);
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer);
        r.put("service", x.service); r.put("pickup", x.pickup); r.put("dropoff", x.dropoff);
        r.put("runner", x.runner); r.put("total", x.total); r.put("status", x.status);
        if (x.note != null && !x.note.isBlank()) r.put("note", x.note);
        if ("已送达".equals(x.status)) r.put("msg", "订单已送达，可开发票");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byService = new LinkedHashMap<String, Object>();
        for (String s : SERVICES.keySet()) {
            long n = orders.stream().filter(o -> s.equals(o.service)).count();
            if (n > 0) byService.put(s, n + " 单");
        }
        Map<String, Object> byRunner = new LinkedHashMap<String, Object>();
        for (var e : RUNNERS.entrySet()) {
            long n = orders.stream().filter(o -> o.runner.equals(e.getValue()[0])).count();
            byRunner.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        long pending = orders.stream().filter(o -> "待接单".equals(o.status)).count();
        long delivering = orders.stream().filter(o -> "配送中".equals(o.status)).count();
        double revenue = orders.stream().filter(o -> "已送达".equals(o.status)).mapToDouble(o -> o.total).sum();
        double expected = orders.stream().filter(o -> !"已取消".equals(o.status)).mapToDouble(o -> o.total).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalOrders", orders.size());
        r.put("pending", pending);
        r.put("delivering", delivering);
        r.put("done", orders.stream().filter(o -> "已送达".equals(o.status)).count());
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byService", byService);
        r.put("byRunner", byRunner);
        r.put("advice", "月卡用户复购率高可推季卡；雨天是加价高峰也是投诉高峰需提前告知；医院代排队可做固定套餐");
        return r;
    }
}
