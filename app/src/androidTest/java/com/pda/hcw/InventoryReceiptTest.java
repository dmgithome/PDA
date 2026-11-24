package com.pda.hcw;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InventoryReceiptTest {
    private JSONObject receipt() throws Exception {
        return new JSONObject().put("status",200).put("data",new JSONObject().put("taskId","A")
                .put("batchId",1).put("acceptedRfids",new JSONArray(Arrays.asList("b","a")))
                .put("plannedCount",1).put("scannedCount",2).put("normalCount",1)
                .put("missingCount",0).put("surplusCount",1));
    }

    @Test public void onlyACompleteReceiptForTheSubmittedTaskAcknowledgesABatch() throws Exception {
        JSONArray sent = new JSONArray(Arrays.asList("a","b"));
        assertEquals(1,ConsumableApiService.parseScanReceipt(receipt().toString(),"A",sent).surplusCount);
        for(String key : new String[]{"acceptedRfids","plannedCount","taskId","batchId","scannedCount"}) {
            JSONObject incomplete = receipt(); incomplete.getJSONObject("data").remove(key);
            try { ConsumableApiService.parseScanReceipt(incomplete.toString(),"A",sent); fail(key); }
            catch(Exception expected) {}
        }
        for(JSONArray invalid : new JSONArray[]{new JSONArray(Arrays.asList("a","a")),new JSONArray(Arrays.asList("a","c"))}) {
            JSONObject wrong = receipt(); wrong.getJSONObject("data").put("acceptedRfids",invalid);
            try { ConsumableApiService.parseScanReceipt(wrong.toString(),"A",sent); fail(); } catch(Exception expected) {}
        }
        try { ConsumableApiService.parseScanReceipt(receipt().toString(),"B",sent); fail(); } catch(Exception expected) {}
    }

    @Test public void acknowledgmentOnlyRemovesSubmittedTagsFromTheOriginalTask() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CheckoutRecordDatabase db = CheckoutRecordDatabase.getInstance(ctx);
        db.recordPendingScan("A","x"); db.recordPendingScan("A","x"); db.recordPendingScan("A","y"); db.recordPendingScan("B","x");
        db.close();
        assertEquals(Arrays.asList("x","y"),db.pendingScans("A"));
        db.acknowledgeScans("A",Collections.singletonList("x"));
        assertEquals(Collections.singletonList("y"),db.pendingScans("A"));
        assertEquals(Collections.singletonList("x"),db.pendingScans("B"));
    }
}
