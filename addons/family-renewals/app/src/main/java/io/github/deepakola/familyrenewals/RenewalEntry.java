package io.github.deepakola.familyrenewals;

import java.util.UUID;

final class RenewalEntry {
    String id;
    String member;
    String category;
    String service;
    String reference;
    String amount;
    String lastPaid;
    String dueDate;
    String notes;

    RenewalEntry() {
        id = UUID.randomUUID().toString();
        member = "";
        category = "Mobile";
        service = "";
        reference = "";
        amount = "";
        lastPaid = "";
        dueDate = "";
        notes = "";
    }
}
