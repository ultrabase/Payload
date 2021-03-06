/*
 * Copyright (c) 2019 Jonah Seguin.  All rights reserved.  You may not modify, decompile, distribute or use any code/text contained in this document(plugin) without explicit signed permission from Jonah Seguin.
 * www.jonahseguin.com
 */

package com.jonahseguin.payload.command.commands;

import com.google.inject.Inject;
import com.jonahseguin.payload.PayloadAPI;
import com.jonahseguin.payload.PayloadPlugin;
import com.jonahseguin.payload.base.PayloadPermission;
import com.jonahseguin.payload.command.CmdArgs;
import com.jonahseguin.payload.command.PayloadCommand;

public class CmdDebug implements PayloadCommand {

    private final PayloadPlugin payloadPlugin;
    private final PayloadAPI api;

    @Inject
    public CmdDebug(PayloadPlugin payloadPlugin, PayloadAPI api) {
        this.payloadPlugin = payloadPlugin;
        this.api = api;
    }

    @Override
    public void execute(CmdArgs args) {
        if (args.length() == 0) {
            args.msg("&7Payload: Debug is {0} &7(use &e/payload debug [on/off]&7 to toggle)", (payloadPlugin.isDebug() ? "&aon" : "&coff"));
        } else {
            String toggle = args.arg(0).toLowerCase();
            if (toggle.startsWith("on")) {
                payloadPlugin.setDebug(true);
                api.getCaches().values().forEach(cache -> cache.setDebug(true));
                args.msg("&7Payload: Debug &aon");
            } else if (toggle.startsWith("off")) {
                payloadPlugin.setDebug(false);
                args.msg("&7Payload: Debug &coff");
                api.getCaches().values().forEach(cache -> cache.setDebug(false));
            }
        }
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String[] aliases() {
        return new String[]{"deb", "bug"};
    }

    @Override
    public String desc() {
        return "View debug status and toggle";
    }

    @Override
    public PayloadPermission permission() {
        return PayloadPermission.DEBUG;
    }

    @Override
    public String usage() {
        return "[on/off]";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public int minArgs() {
        return 0;
    }

}
