package com.ubiqsmart.datasource.api

import io.reactivex.Observable
import retrofit2.http.GET

interface CryptoCompareApi {

  @GET("public?command=returnChartData&currencyPair={pair}&start={startTime}&&end=9999999999&period={period}")
  fun getPriceChart(): Observable<Int>

}
