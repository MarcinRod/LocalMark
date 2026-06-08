package pl.marrod.localmark

import android.app.Application
import com.google.android.libraries.places.api.Places

import pl.marrod.localmark.di.AppContainer

class LocalMarkApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

}