package com.github.nyxiereal.layoutcontroller.util

import android.view.View
import android.widget.LinearLayout
import com.github.nyxiereal.layoutcontroller.patchers.*

val patches = arrayOf(
    CallButtonsPatch(),
    ChannelsInviteButtonPatch(),
    DmSearchBoxPatch(),
    MembersInviteButtonPatch(),
    NitroGiftButtonPatch(),
    NotesPatch(),
    UntrustedDomainPatch(),
    WelcomeButtonPatch(),
    DMWavePatch(),
    CrownsPatch(),
    StudentHubsButtonPatch(),
)

fun View.hideCompletely() {
    visibility = View.GONE
    layoutParams = LinearLayout.LayoutParams(0, 0)
}