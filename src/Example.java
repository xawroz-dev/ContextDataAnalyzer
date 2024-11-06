package org.example;

import java.util.HashMap;
import java.util.Map;

public class Example {
    public static final String CUSTOMER_NAME = "customerName";
    private static final String ACE_CONTEXT_DATA = "Ace";

    public void process() {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> contextData = (Map<String, Object>) map.get(ACE_CONTEXT_DATA);
        contextData.put(CUSTOMER_NAME, "saroj");
        contextData.put("address", "napal");
        contextData.put("accountId", "23423423");

        String orderIdKey = "orderId";
        contextData.get(orderIdKey);
    }
}