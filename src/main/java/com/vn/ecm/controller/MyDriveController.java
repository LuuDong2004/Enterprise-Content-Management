package com.vn.ecm.controller;

import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.user.UserService;
import io.jmix.core.DataManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/drive")
public class MyDriveController {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionService permissionService;



}
