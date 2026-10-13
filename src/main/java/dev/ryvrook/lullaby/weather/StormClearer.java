package dev.ryvrook.lullaby.weather;

import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.common.map.IWeightedMap;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.type.environment.config.WeatherForecast;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2IntMap;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gives a clear morning after a skip. For every environment whose current weather is a storm it writes the
 * most likely calm weather of that environment's forecast for this hour. The hour is claimed on the weather
 * resource first, so the weather system does not reroll over it, and the next hour change rolls weather as
 * usual. Nothing is forced and nothing is saved to the world config. Runs on the world thread.
 */
public final class StormClearer {
    private StormClearer() {
    }

    /** Returns how many environments were calmed. Leaves weather forced by an admin alone. */
    public static int clear(Store<EntityStore> store, WorldTimeResource time, List<String> stormWords) {
        if (stormWords.isEmpty()) {
            return 0;
        }
        WeatherResource weather = store.getResource(WeatherResource.getResourceType());
        if (weather == null || weather.getForcedWeatherIndex() != 0) {
            return 0;
        }
        int hour = time.getCurrentHour();
        weather.compareAndSwapHour(hour);
        Int2IntMap current = weather.getEnvironmentWeather();
        var environments = Environment.getAssetMap();
        var weathers = Weather.getAssetMap();
        int calmed = 0;
        for (Map.Entry<String, Environment> entry : environments.getAssetMap().entrySet()) {
            int envIndex = environments.getIndex(entry.getKey());
            if (envIndex < 0 || !current.containsKey(envIndex)) {
                continue;
            }
            Weather now = weathers.getAsset(current.get(envIndex));
            if (now == null || !isStorm(now.getId(), stormWords)) {
                continue;
            }
            IWeightedMap<WeatherForecast> forecast = entry.getValue().getWeatherForecast(hour);
            if (forecast == null) {
                continue;
            }
            WeatherForecast[] best = {null};
            forecast.forEachEntry((candidate, weight) -> {
                if (candidate == null || isStorm(candidate.getWeatherId(), stormWords)) {
                    return;
                }
                if (best[0] == null || weight > best[0].getWeight()) {
                    best[0] = candidate;
                }
            });
            if (best[0] != null && best[0].getWeatherIndex() >= 0) {
                current.put(envIndex, best[0].getWeatherIndex());
                calmed++;
            }
        }
        return calmed;
    }

    /** True when the weather id contains one of the configured words, ignoring case. */
    static boolean isStorm(String id, List<String> stormWords) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String word : stormWords) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
