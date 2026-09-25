package cn.xiaofuge.r.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 跑腿代办 REST 接口。
 * 提供：服务价目 / 跑男列表 / 下单 / 订单查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class RController {

    private final RStore store;

    public RController(RStore store) {
        this.store = store;
    }

    /** 服务价目表 */
    @GetMapping("/services")
    public Map<String, Object> services() {
        return store.serviceList();
    }

    /** 跑男列表 */
    @GetMapping("/runners")
    public Map<String, Object> runners() {
        return store.runnerList();
    }

    /** 下单 */
    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, Object> body) {
        return store.place(str(body, "customer"), str(body, "phone"), str(body, "service"),
                str(body, "pickup"), str(body, "dropoff"), str(body, "runnerId"), str(body, "note"));
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** 订单查询 */
    @GetMapping("/order/info")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
