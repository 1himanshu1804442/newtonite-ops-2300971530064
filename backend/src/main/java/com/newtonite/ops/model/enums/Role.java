package com.newtonite.ops.model.enums;

/**
 * Roles for team members governing workflow permissions.
 * LEAD and ADMIN roles possess the authority to approve transitions to RESOLVED and CLOSED.
 */
public enum Role {
    VIEWER,
    MEMBER,
    LEAD,
    ADMIN
}
