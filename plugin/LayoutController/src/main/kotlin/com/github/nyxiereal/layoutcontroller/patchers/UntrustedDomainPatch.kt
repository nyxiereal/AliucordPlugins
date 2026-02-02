package com.github.nyxiereal.layoutcontroller.patchers

import com.github.nyxiereal.layoutcontroller.patchers.base.BasePatcher
import com.github.nyxiereal.layoutcontroller.util.Description
import com.github.nyxiereal.layoutcontroller.util.Key
import com.discord.stores.StoreMaskedLinks
import de.robv.android.xposed.XC_MethodHook

class UntrustedDomainPatch : BasePatcher(
    key = Key.UNTRUSTED_DOMAINS_KEY,
    description = Description.UNTRUSTED_DOMAINS_DESCRIPTION,
    requiresRestart = false,
    classMember = StoreMaskedLinks::class.java.getDeclaredMethod(
        "isTrustedDomain",
        String::class.java,
        String::class.java
    )
) {
    override fun patchBody(callFrame: XC_MethodHook.MethodHookParam) {
        callFrame.result = true
    }
}