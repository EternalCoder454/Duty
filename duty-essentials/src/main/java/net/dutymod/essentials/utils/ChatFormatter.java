package net.dutymod.essentials.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

public class ChatFormatter {

    public static MutableComponent format(String input) {
        char[] b = input.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return Component.literal(new String(b));
    }

    public static MutableComponent flattenToLiteral(Component component) {
        MutableComponent builder = Component.empty();
        component.visit((style, string) -> {
            builder.append(Component.literal(string).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return builder;
    }
}
