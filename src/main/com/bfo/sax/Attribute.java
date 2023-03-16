package com.bfo.sax;

import java.util.*;

final class Attribute {

    final String qName, type, mode, value;

    Attribute(String qName, String type, String mode, String value) {
        this.qName = qName;
        this.type = type;
        this.mode = mode != null ? mode : "#FIXED";
        if (value != null && !"CDATA".equals(type)) {
            // Normalize default
            StringBuilder sb = new StringBuilder();
            boolean ws = false;
            for (int j=0;j<value.length();j++) {
                char c = value.charAt(j);
                if (c == ' ') {
                    if (sb.length() > 0) {
                        ws = true;
                    }
                } else {
                    if (ws) {
                        sb.append(' ');
                        ws = false;
                    }
                    sb.append(c);
                }
            }
            value = sb.toString();
        }
        this.value = value;
    }

    public String getQName() {
        return qName;
    }
    public String getType() {
        return type;
    }
    public String getMode() {
        return mode;
    }
    public String getDefaultValue() {
        return value;
    }

}
