package app.lawnchairlite.data

import app.lawnchairlite.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartspaceWeatherLabelsTest {
    @Test
    fun mapsOpenMeteoCodesToStableResources() {
        assertEquals(R.string.weather_clear, SmartspaceWeatherLabels.labelRes(0))
        assertEquals(R.string.weather_partly_cloudy, SmartspaceWeatherLabels.labelRes(2))
        assertEquals(R.string.weather_drizzle, SmartspaceWeatherLabels.labelRes(55))
        assertEquals(R.string.weather_rain, SmartspaceWeatherLabels.labelRes(80))
        assertEquals(R.string.weather_snow, SmartspaceWeatherLabels.labelRes(86))
        assertEquals(R.string.weather_storm, SmartspaceWeatherLabels.labelRes(99))
        assertEquals(R.string.weather_generic, SmartspaceWeatherLabels.labelRes(-1))
    }
}
