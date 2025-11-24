package com.vn.ecm.view.blockinheritance;

/**
 * Enum định nghĩa các action khi block inheritance
 */
public enum BlockInheritanceAction {
    /**
     * Chuyển các quyền kế thừa thành quyền trực tiếp
     */
    CONVERT,

    /**
     * Xóa tất cả quyền kế thừa
     */
    REMOVE,

    /**
     * Hủy bỏ action
     */
    CANCEL
}
