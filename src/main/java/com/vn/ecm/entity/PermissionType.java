package com.vn.ecm.entity;


import io.jmix.core.metamodel.datatype.EnumClass;

public enum PermissionType implements EnumClass<Integer> {
    READ(1),
    CREATE(2),
    MODIFY(4),
    FULL(8);
    private int value;

    PermissionType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public Integer getId() {
        return value;
    }

    public static PermissionType fromId(Integer id) {
        if (id == null)
            return null;
        for (PermissionType type : PermissionType.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        switch (this) {
            case READ:
                return "READ";
            case CREATE:
                return "CREATE";
            case MODIFY:
                return "MODIFY";
            case FULL:
                return "FULL ACCESS";
            default:
                return super.toString();
        }
    }

}
