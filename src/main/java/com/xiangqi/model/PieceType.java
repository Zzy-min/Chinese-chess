package com.xiangqi.model;

/**
 * 棋子类型枚举
 */
public enum PieceType {
    JIANG("将"), SHI("士"), XIANG("象"), MA("马"),
    CHE("车"), PAO("炮"), ZU("兵"),
    SHUAI("帅"), SHI_RED("仕"), XIANG_RED("相"),
    MA_RED("馬"), CHE_RED("車"), PAO_RED("砲"),
    ZU_RED("卒");

    private String displayName;

    PieceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isGeneral() {
        return this == JIANG || this == SHUAI;
    }

    public boolean isSoldier() {
        return this == ZU || this == ZU_RED;
    }
}
