package com.example.mycommute.network;

/** Deliberately UNCONNECTED: no fabricated real-time values before access and stop identifiers exist. */
public final class ProviderContracts {
    public interface WeatherProvider {
        void fetchForecast(double lat,double lon,long targetMillis,Callback<Weather> callback);
    }
    public interface TransitProvider {
        void fetchArrival(String stopId,String line,String direction,Callback<TransitArrival> callback);
    }
    public interface Callback<T> { void onSuccess(T result); void onError(String error); }
    public static class Weather {
        public boolean isRaining, unsafeForBike;
        public long observedOrForecastTime;
        public String source,detail;
    }
    public static class TransitArrival {
        public String line,stopId,direction;
        public int minutesUntilArrival;
        public long fetchedAtMillis;
    }
    private ProviderContracts() { }
}
