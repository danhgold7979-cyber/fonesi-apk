package vn.fonesi.app

import android.app.Application

/**
 * Process creation must stay side-effect free. Background workers can create this Application
 * while Fonesi is locked/closed; runtime activation is performed only by the visible unlocked UI.
 */
class FonesiApplication : Application()
