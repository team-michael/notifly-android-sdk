package tech.notifly.sample

import android.app.Application
import tech.notifly.Notifly

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifly.initialize(
            context = applicationContext,
            projectId = "b80c3f0e2fbd5eb986df4f1d32ea2871",
            username = "minyong",
            password = "000000"
        )
    }
}
