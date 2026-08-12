package com.purut.domain.user;

public enum Role {
    STUDENT,
    TEACHER,
    ADMIN;

    public boolean isStaff() {
        return this == TEACHER || this == ADMIN;
    }
}
