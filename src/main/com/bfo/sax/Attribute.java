package com.bfo.sax;

import java.util.*;

final class Attribute {

    final String type, mode, value;

    Attribute(String type, String mode, String value) {
        this.type = type;
        this.mode = mode;
        this.value = value;
    }

    public String getType() {
        return type;
    }
    public String getMode() {
        return mode;
    }
    public String getDefault() {
        return value;
    }

}
