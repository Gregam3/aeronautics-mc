package com.example.createstockexchange.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;
import java.util.UUID;

public final class ReceiptHelper {
    private ReceiptHelper() {}

    public static ItemStack createBuyReceipt(String companyName, String itemName,
                                              int quantity, int pricePerUnit) {
        int total = quantity * pricePerUnit;
        String id = UUID.randomUUID().toString().substring(0, 8);
        String text = "=== RECEIPT ===\n\n"
                + companyName + "\n"
                + "ID: " + id + "\n\n"
                + "PURCHASE\n"
                + "-----------\n"
                + "Item:  " + itemName + "\n"
                + "Qty:   " + quantity + "\n"
                + "Price: " + pricePerUnit + " sp ea\n"
                + "Total: " + total + " sp\n"
                + "-----------";
        return buildBook("Receipt", companyName, text);
    }

    public static ItemStack createDepositReceipt(String companyName, String itemName,
                                                  int quantity, int pricePerUnit) {
        int total = quantity * pricePerUnit;
        String id = UUID.randomUUID().toString().substring(0, 8);
        String text = "=== RECEIPT ===\n\n"
                + companyName + "\n"
                + "ID: " + id + "\n\n"
                + "DEPOSIT\n"
                + "-----------\n"
                + "Item:     " + itemName + "\n"
                + "Qty:      " + quantity + "\n"
                + "Received: " + pricePerUnit + " sp ea\n"
                + "Total:    " + total + " sp\n"
                + "-----------";
        return buildBook("Receipt", companyName, text);
    }

    private static ItemStack buildBook(String title, String author, String pageText) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal(pageText))
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                pages,
                true
        ));
        return book;
    }
}
