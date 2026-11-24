package com.pda.hcw;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pda.hcw.inventory.InventoryPlanItem;
import com.pda.hcw.inventory.InventorySession;
import com.pda.hcw.rfid.EpcFormatter;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class StockWorkflowTest {
    private static final String A = "100000000000000001", B = "100000000000000002";
    private final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private ServerSocket http;
    private CountDownLatch sent = new CountDownLatch(1),
            allow = new CountDownLatch(1),
            bothGroups = new CountDownLatch(2);
    private AtomicReference<JSONObject> posted = new AtomicReference<>();
    private AtomicInteger pages = new AtomicInteger(),
            successes = new AtomicInteger(),
            failures = new AtomicInteger();
    private volatile String mode = "";
    private final CountDownLatch replayed = new CountDownLatch(1);
    private final AtomicReference<String> originalBody = new AtomicReference<>();
    private final AtomicBoolean sameReplay = new AtomicBoolean();
    private final AtomicInteger transferAttempts = new AtomicInteger();

    private void note(String msg) {
        Log.i("StockWorkflowTest", msg);
    }

    @Before
    public void localBackend() throws Exception {
        http = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        Thread accept =
                new Thread(
                        () -> {
                            while (!http.isClosed())
                                try {
                                    Socket conn = http.accept();
                                    new Thread(() -> reply(conn)).start();
                                } catch (IOException e) {
                                    return;
                                }
                        });
        accept.setDaemon(true);
        accept.start();
        NetworkConfig.getInstance(ctx).setApiBaseUrl("http://127.0.0.1:" + http.getLocalPort());
        ctx.getSharedPreferences("settings_prefs", 0)
                .edit()
                .putInt("cabinet_id", 1)
                .putString("sd_code", "test")
                .apply();
        ctx.getSharedPreferences("locations_prefs", 0)
                .edit()
                .putInt("default_return_location_id", 3)
                .apply();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () ->
                                SessionManager.getInstance(ctx)
                                        .startSession(
                                                new UserInfo(
                                                        "17",
                                                        "review",
                                                        "测试操作员",
                                                        UserRole.ADMIN,
                                                        "test-value",
                                                        "test-session")));
    }

    @After
    public void stop() throws Exception {
        allow.countDown();
        http.close();
    }

    static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void set(Object o, String name, Object val) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, val);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static Object call(Object o, String name, Class<?>[] types, Object... args) {
        try {
            Method m = o.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(o, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    void waitFor(BooleanSupplier criterion) {
        long end = SystemClock.elapsedRealtime() + 6000;
        do {
            if (criterion.getAsBoolean()) return;
            SystemClock.sleep(35);
        } while (SystemClock.elapsedRealtime() < end);
        fail("wait " + mode);
    }

    static JSONObject ok(Object data) throws Exception {
        return new JSONObject().put("status", 200).put("data", data);
    }

    private void reply(Socket peer) {
        try (Socket conn = peer) {
            conn.setSoTimeout(10000);
            BufferedReader input =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String request = input.readLine();
            if (request == null) return;
            String path = request.split(" ")[1], header;
            int len = 0;
            while ((header = input.readLine()) != null && !header.isEmpty()) {
                int i = header.indexOf(':');
                if (i > 0 && header.substring(0, i).equalsIgnoreCase("content-length"))
                    len = Integer.parseInt(header.substring(i + 1).trim());
            }
            // 测试请求使用 UTF-8。按 JSON 边界识别正文，避免字节数和字符数不一致。
            JSONObject data = new JSONObject();
            if (len > 0) {
                StringBuilder body = new StringBuilder();
                int nest = 0;
                boolean str = false, escape = false;
                do {
                    int c = input.read();
                    if (c < 0) break;
                    body.append((char) c);
                    if (escape) {
                        escape = false;
                        continue;
                    }
                    if (str && c == 92) {
                        escape = true;
                        continue;
                    }
                    if (c == 34) {
                        str = !str;
                        continue;
                    }
                    if (!str) {
                        if (c == '{' || c == '[') nest++;
                        if (c == '}' || c == ']') nest--;
                    }
                } while (nest > 0);
                data = new JSONObject(body.toString());
            }
            JSONObject response;
            if (path.contains("submit_from_pda") || path.contains("hasOrderInbound")) {
                posted.set(data);
                sent.countDown();
                if (mode.equals("lost") && transferAttempts.incrementAndGet() == 1) {
                    originalBody.set(data.toString());
                    allow.await(10, TimeUnit.SECONDS);
                    return;
                }
                if (mode.equals("lost")) {
                    sameReplay.set(data.toString().equals(originalBody.get()));
                    replayed.countDown();
                }
                allow.await(10, TimeUnit.SECONDS);
                if (path.contains("hasOrderInbound")) {
                    JSONArray denied = new JSONArray();
                    if (mode.equals("mixedInbound"))
                        denied.put(new JSONObject().put("rfid", B).put("reason", "已在库"));
                    response =
                            ok(
                                    new JSONObject()
                                            .put("accepted", new JSONArray().put(A))
                                            .put("rejected", denied));
                    if (denied.length() > 0) response.put("status", 207);
                } else
                    response = ok(new JSONObject().put("success_count", 1).put("order_count", 1));
            } else if (path.contains("preview_by_barcode")) {
                response =
                        ok(
                                new JSONObject()
                                        .put("code", "C")
                                        .put("name", "test")
                                        .put("current_location_id", "1"));
            } else if (path.contains("createTakeAndReturnLog")) {
                if ("1".equals(data.optString("from_location_id"))) {
                    successes.incrementAndGet();
                    response = ok(new JSONObject().put("recordId", 101).put("returnCount", 1));
                } else {
                    failures.incrementAndGet();
                    response = new JSONObject().put("status", 409).put("message", "库存位置已改变");
                }
                bothGroups.countDown();
            } else if (path.contains("inventoryTask/detail")) {
                pages.incrementAndGet();
                android.net.Uri query = android.net.Uri.parse(path);
                int page = Integer.parseInt(query.getQueryParameter("page"));
                int size = Integer.parseInt(query.getQueryParameter("pagesize"));
                int offset = (page - 1) * size;
                int n = Math.min(size, 10001 - offset);
                if (mode.equals("earlyScan") && page == 2) {
                    sent.countDown();
                    allow.await(8, TimeUnit.SECONDS);
                }
                JSONArray rows = new JSONArray();
                for (int i = 0; i < n; i++)
                    rows.put(
                            new JSONObject()
                                    .put("rfid", "tag" + (offset + i))
                                    .put("snapshotStatus", "inStock"));
                response =
                        ok(
                                new JSONObject()
                                        .put(
                                                "detail",
                                                new JSONObject()
                                                        .put("total", 10001)
                                                        .put("page", page)
                                                        .put("pagesize", size)
                                                        .put("items", rows)));
            } else response = ok(new JSONArray());
            if (data.has("request_id")) response.put("request_id", data.getString("request_id"));
            byte[] answer = response.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream out = conn.getOutputStream();
            out.write(
                    ("HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: application/json; charset=UTF-8\r\n"
                                    + "Connection: close\r\n"
                                    + "Content-Length: "
                                    + answer.length
                                    + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            out.write(answer);
            out.flush();
        } catch (Exception e) {
            Log.w("StockWorkflowTest", "loopback:" + e.getClass().getSimpleName());
        }
    }

    @Test
    public void identifierPadding() {
        String id = "123456789012345678";
        assertEquals(id, EpcFormatter.normalizeOnReceive("000000" + id));
        note("PDA canonical identifier length=18");
    }

    @Test
    public void transferAcknowledgesOnlySubmittedTags() throws Exception {
        mode = "transfer";
        try (ActivityScenario<ConsumableTransferActivity> screen =
                ActivityScenario.launch(ConsumableTransferActivity.class)) {
            screen.onActivity(
                    a -> {
                        set(a, "selectedCenterId", 3);
                        call(a, "handleBarcodeScan", new Class[] {String.class}, A);
                    });
            waitFor(
                    () -> {
                        AtomicBoolean v = new AtomicBoolean();
                        screen.onActivity(a -> v.set(((Map) get(a, "selectedByRfid")).size() == 1));
                        return v.get();
                    });
            screen.onActivity(a -> a.findViewById(R.id.transfer_submit_button).performClick());
            assertTrue(sent.await(5, TimeUnit.SECONDS));
            assertEquals(A, posted.get().getJSONArray("rfid_list").getString(0));
            screen.onActivity(a -> call(a, "handleBarcodeScan", new Class[] {String.class}, B));
            waitFor(
                    () -> {
                        AtomicBoolean v = new AtomicBoolean();
                        screen.onActivity(a -> v.set(((Map) get(a, "selectedByRfid")).size() == 2));
                        return v.get();
                    });
            allow.countDown();
            waitFor(
                    () -> {
                        AtomicBoolean v = new AtomicBoolean();
                        screen.onActivity(
                                a ->
                                        v.set(
                                                ((Map) get(a, "selectedByRfid")).size() == 1
                                                        && ((Map) get(a, "selectedByRfid"))
                                                                .containsKey(B)));
                        return v.get();
                    });
        }
    }

    @Test
    public void inboundKeepsNewScansForSameOrder() throws Exception {
        mode = "inbound";
        InboundOrder order = new InboundOrder();
        order.code = "fixture-order";
        order.total = 2;
        order.plannedRfids.add(A);
        order.plannedRfids.add(B);
        try (ActivityScenario<InboundAcceptanceActivity> screen =
                ActivityScenario.launch(InboundAcceptanceActivity.class)) {
            screen.onActivity(
                    a -> {
                        ((List) get(a, "orders")).add(order);
                        call(
                                a,
                                "performAddDelta",
                                new Class[] {InboundOrder.class, String.class},
                                order,
                                A);
                        Set<String> live =
                                (Set<String>) ((Map) get(a, "deltaByOrder")).get(order.code);
                        call(
                                a,
                                "finalSubmitWithSpdId",
                                new Class[] {InboundOrder.class, int.class, int.class, List.class},
                                order,
                                3,
                                1,
                                new ArrayList<>(live));
                    });
            assertTrue(sent.await(5, TimeUnit.SECONDS));
            assertEquals(1, posted.get().getJSONArray("items").length());
            screen.onActivity(
                    a -> {
                        call(
                                a,
                                "performAddDelta",
                                new Class[] {InboundOrder.class, String.class},
                                order,
                                B);
                        assertEquals(2, order.matchedInSession);
                    });
            allow.countDown();
            waitFor(
                    () -> {
                        AtomicBoolean v = new AtomicBoolean();
                        screen.onActivity(
                                a ->
                                        v.set(
                                                java.util.Collections.singleton(B)
                                                        .equals(
                                                                ((Map) get(a, "deltaByOrder"))
                                                                        .get(order.code))));
                        return v.get();
                    });
        }
    }

    @Test
    public void mixedOriginReturnSavesOnlySuccessfulGroup() throws Exception {
        mode = "checkout";
        Intent in =
                new Intent(ctx, ConsumableCheckoutNewActivity.class)
                        .putExtra("operation_mode", "return");
        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int before =
                CheckoutRecordManager.getInstance(ctx)
                        .getRecordsByTimeFilter(TimeFilter.TODAY, "17")
                        .size();
        try (ActivityScenario<ConsumableCheckoutNewActivity> screen = ActivityScenario.launch(in)) {
            screen.onActivity(
                    a -> {
                        for (int i = 1; i <= 2; i++) {
                            ConsumableItem item = new ConsumableItem();
                            item.setRfid(i == 1 ? A : B);
                            item.setProductCode("P" + i);
                            item.setCommonName("Review");
                            item.setQuantity(1);
                            item.setUnit("u");
                            item.setCurrentLocationId(i);
                            ((Map) get(a, "selectedItemsByRfid")).put(item.getRfid(), item);
                            call(a, "addConsumableItem", new Class[] {ConsumableItem.class}, item);
                        }
                        call(a, "confirmCheckout", new Class[] {});
                    });
            assertTrue(bothGroups.await(6, TimeUnit.SECONDS));
            waitFor(
                    () -> {
                        AtomicBoolean v = new AtomicBoolean();
                        screen.onActivity(
                                a -> v.set(a.findViewById(R.id.confirm_button).isEnabled()));
                        return v.get();
                    });
            SystemClock.sleep(180);
            screen.onActivity(
                    a -> {
                        assertEquals(1, ((List) get(a, "consumableGroupList")).size());
                        assertEquals(
                                before + 1,
                                CheckoutRecordManager.getInstance(ctx)
                                        .getRecordsByTimeFilter(TimeFilter.TODAY, "17")
                                        .size());
                    });
            assertEquals(1, successes.get());
            assertEquals(1, failures.get());
            screen.onActivity(a -> call(a, "confirmCheckout", new Class[] {}));
            waitFor(() -> failures.get() == 2);
            assertEquals(1, successes.get());
        }
    }

    @Test
    public void partialInboundSettlesEveryReturnedLabel() throws Exception {
        mode = "mixedInbound";
        InboundOrder order = new InboundOrder();
        order.code = "partial-order";
        order.total = 2;
        try (ActivityScenario<InboundAcceptanceActivity> screen =
                ActivityScenario.launch(InboundAcceptanceActivity.class)) {
            screen.onActivity(
                    a -> {
                        ((List) get(a, "orders")).add(order);
                        for (String id : new String[] {A, B}) {
                            InboundOrderDetail detail = new InboundOrderDetail();
                            detail.rfid = id;
                            detail.code = id.equals(A) ? "TEST-A" : "TEST-B";
                            order.detailByRfid.put(id, detail);
                            order.plannedRfids.add(id);
                            call(
                                    a,
                                    "performAddDelta",
                                    new Class[] {InboundOrder.class, String.class},
                                    order,
                                    id);
                        }
                        Set<String> pending =
                                (Set<String>) ((Map) get(a, "deltaByOrder")).get(order.code);
                        call(
                                a,
                                "finalSubmitWithSpdId",
                                new Class[] {InboundOrder.class, int.class, int.class, List.class},
                                order,
                                3,
                                1,
                                new ArrayList<>(pending));
                    });
            assertTrue(sent.await(5, TimeUnit.SECONDS));
            assertEquals(2, posted.get().getJSONArray("items").length());
            allow.countDown();
            waitFor(
                    () -> {
                        AtomicBoolean done = new AtomicBoolean();
                        screen.onActivity(
                                a ->
                                        done.set(
                                                !((Map) get(a, "deltaByOrder"))
                                                        .containsKey(order.code)));
                        return done.get();
                    });
        }
    }

    @Test
    public void reopeningPageRecoversOriginalRequestAfterReplyWasLost() throws Exception {
        mode = "lost";
        try (ActivityScenario<ConsumableTransferActivity> screen =
                ActivityScenario.launch(ConsumableTransferActivity.class)) {
            screen.onActivity(
                    a -> {
                        set(a, "selectedCenterId", 3);
                        call(a, "handleBarcodeScan", new Class[] {String.class}, A);
                    });
            waitFor(
                    () -> {
                        AtomicBoolean ready = new AtomicBoolean();
                        screen.onActivity(
                                a -> ready.set(((Map) get(a, "selectedByRfid")).containsKey(A)));
                        return ready.get();
                    });
            screen.onActivity(a -> a.findViewById(R.id.transfer_submit_button).performClick());
            assertTrue(sent.await(5, TimeUnit.SECONDS));
        }
        allow.countDown();
        try (ActivityScenario<ConsumableTransferActivity> screen =
                ActivityScenario.launch(ConsumableTransferActivity.class)) {
            assertTrue(replayed.await(6, TimeUnit.SECONDS));
            assertTrue(sameReplay.get());
            waitFor(
                    () -> {
                        AtomicBoolean done = new AtomicBoolean();
                        screen.onActivity(
                                a ->
                                        done.set(
                                                !((SubmissionQueue) get(a, "submissions"))
                                                        .hasPending("batch")));
                        return done.get();
                    });
        }
    }

    @Test
    public void inventoryIncludesPlannedItemOnLastPage() throws Exception {
        mode = "inventory";
        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<ConsumableApiService.InventoryTaskDetailResult> result =
                new AtomicReference<>();
        ConsumableApiService.getInstance(ctx)
                .fetchInventoryTaskDetail(
                        "review-task",
                        new ConsumableApiService.ApiCallback<
                                ConsumableApiService.InventoryTaskDetailResult>() {
                            public void onSuccess(
                                    ConsumableApiService.InventoryTaskDetailResult r) {
                                result.set(r);
                                got.countDown();
                            }

                            public void onFailure(Exception e) {
                                note(e.toString());
                                got.countDown();
                            }
                        });
        assertTrue(got.await(8, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(10001, result.get().items.size());
        assertEquals(11, pages.get());
        InventorySession session = new InventorySession("review-task");
        List<InventoryPlanItem> plan = new ArrayList<>();
        for (ConsumableApiService.InventoryTaskDetailItem item : result.get().items)
            plan.add(new InventoryPlanItem(item.rfid, 1, "review", "", "", "", "", null, null));
        session.loadPlan(plan);
        session.onTag("tag10000");
        assertFalse(session.getSurplusRfids().contains("tag10000"));
        assertEquals(1, session.getCounted());
    }

    @Test
    public void scanWhilePlanIsLoadingCountsWhenLastPageArrives() throws Exception {
        mode = "earlyScan";
        InventoryTask task = new InventoryTask();
        task.setTaskId("early-scan-task");
        task.setRawStatus("running");
        try (ActivityScenario<InventoryActivity> screen =
                ActivityScenario.launch(InventoryActivity.class)) {
            screen.onActivity(
                    a -> call(a, "selectInventoryTask", new Class[] {InventoryTask.class}, task));
            assertTrue(sent.await(5, TimeUnit.SECONDS));
            screen.onActivity(
                    a -> {
                        assertNull(get(a, "inventorySession"));
                        call(a, "handleTagScanned", new Class[] {String.class}, "tag10000");
                    });
            allow.countDown();
            waitFor(
                    () -> {
                        AtomicBoolean done = new AtomicBoolean();
                        screen.onActivity(
                                a -> {
                                    InventorySession loaded =
                                            (InventorySession) get(a, "inventorySession");
                                    if (loaded == null) return;
                                    assertEquals(10001, loaded.getTotalPlanned());
                                    assertEquals(1, loaded.getCounted());
                                    assertEquals(0, loaded.getSurplus());
                                    assertEquals(
                                            "盘毕：1",
                                            ((android.widget.TextView) get(a, "headerCounted"))
                                                    .getText()
                                                    .toString());
                                    done.set(true);
                                });
                        return done.get();
                    });
        }
    }
}
