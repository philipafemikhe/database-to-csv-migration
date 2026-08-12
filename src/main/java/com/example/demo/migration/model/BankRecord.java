package com.example.demo.migration.model;

public record BankRecord(long id, String name, String swiftCode, boolean wallet, Boolean allowInstantTransfer, String code) {
}
