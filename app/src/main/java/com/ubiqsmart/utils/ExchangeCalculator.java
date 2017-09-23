package com.ubiqsmart.utils;

import com.ubiqsmart.interfaces.NetworkUpdateListener;
import com.ubiqsmart.repository.api.EtherscanAPI;
import com.ubiqsmart.repository.data.Currency;
import com.ubiqsmart.repository.data.Token;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

public class ExchangeCalculator {

  public static final BigDecimal ONE_ETHER = new BigDecimal("1000000000000000000");

  private static ExchangeCalculator instance;
  private long lastUpdateTimestamp = 0;
  private double rateForChartDisplay = 1;
  private DecimalFormat formatterUsd = new DecimalFormat("#,###,###.##");
  private DecimalFormat formatterCrypt = new DecimalFormat("#,###,###.####");
  private DecimalFormat formatterCryptExact = new DecimalFormat("#,###,###.#######");

  private ExchangeCalculator() {
  }

  public static ExchangeCalculator getInstance() {
    if (instance == null) {
      instance = new ExchangeCalculator();
    }
    return instance;
  }

  private Currency[] conversionNames = new Currency[] {
      new Currency("ETH", 1, "Ξ"), new Currency("BTC", 0.07, "฿"), new Currency("USD", 0, "$")
  };

  private int index = 0;

  public void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  public double getRateForChartDisplay() {
    return rateForChartDisplay;
  }

  public Currency next() {
    index = (index + 1) % conversionNames.length;
    return conversionNames[index];
  }

  public Currency getCurrent() {
    return conversionNames[index];
  }

  public Currency previous() {
    index = index > 0 ? index - 1 : conversionNames.length - 1;
    return conversionNames[index];
  }

  public Currency getMainCurreny() {
    return conversionNames[2];
  }

  public Currency getEtherCurrency() {
    return conversionNames[0];
  }

  public String getCurrencyShort() {
    return conversionNames[index].getShorty();
  }

  public String displayBalanceNicely(double d) {
    if (index == 2) {
      return displayUsdNicely(d);
    } else {
      return displayEthNicely(d);
    }
  }

  public String displayUsdNicely(double d) {
    return formatterUsd.format(d);
  }

  public String displayEthNicely(double d) {
    return formatterCrypt.format(d);
  }

  public String displayEthNicelyExact(double d) {
    return formatterCryptExact.format(d);
  }

  /**
   * Converts given tokenbalance to ETH
   *
   * @param tokenbalance native token balance
   * @param tokenusd price in USD for each token
   *
   * @return Ether worth of given tokens
   */
  public double convertTokenToEther(double tokenbalance, double tokenusd) {
    return Math.floor((((tokenbalance * tokenusd) / conversionNames[2].getRate()) * 10000)) / 10000;
  }

  public double convertRate(double balance, double rate) {
    if (index == 2) {
      if (balance * rate >= 100000) // dont display cents if bigger than 100k
      {
        return (int) Math.floor(balance * rate);
      }
      return Math.floor(balance * rate * 100) / 100;
    } else {
      if (balance * rate >= 1000) return Math.floor(balance * rate * 10) / 10;
      if (balance * rate >= 100) return Math.floor(balance * rate * 100) / 100;
      return Math.floor(balance * rate * 1000) / 1000;
    }
  }

  public double weiToEther(long weis) {
    return new BigDecimal(weis).divide(ONE_ETHER, 8, BigDecimal.ROUND_DOWN).doubleValue();
  }

  public String convertRateExact(BigDecimal balance, double rate) {
    if (index == 2) {
      return displayUsdNicely(Math.floor(balance.doubleValue() * rate * 100) / 100) + "";
    } else {
      return displayEthNicelyExact(balance.multiply(new BigDecimal(rate)).setScale(7, RoundingMode.CEILING).doubleValue());
    }
  }

  public double convertToUsd(double balance) {
    return Math.floor(balance * getUSDPrice() * 100) / 100;
  }

  /**
   * Used for DetailFragmentOverview "Overall Balance"
   *
   * @param token List of all tokens on an address
   *
   * @return ether price of all tokens combined (exclusive ether balance itself)
   */
  public double sumUpTokenEther(List<Token> token) {
    double summedEther = 0;
    for (Token t : token) {
      if (t.getShorty().equals("ETH")) continue;
      summedEther += convertTokenToEther(t.getBalanceDouble(), t.getUsdprice());
    }
    return summedEther;
  }

  public double getUSDPrice() {
    return Math.floor(conversionNames[2].getRate() * 100) / 100;
  }

  public double getBTCPrice() {
    return Math.floor(conversionNames[1].getRate() * 10000) / 10000;
  }

  public void updateExchangeRates(final String currency, final NetworkUpdateListener update) throws IOException {
    if (lastUpdateTimestamp + 40 * 60 * 1000 > System.currentTimeMillis() && currency.equals(
        conversionNames[2].getName())) { // Dont refresh if not older than 40 min and currency hasnt changed
      return;
    }
    if (!currency.equals(conversionNames[2].getName())) {
      conversionNames[2].setName(currency);
      switch (currency) {
        case "USD":
          conversionNames[2].setShorty("$");
          break;
        case "EUR":
          conversionNames[2].setShorty("€");
          break;
        case "GPB":
          conversionNames[2].setShorty("£");
          break;
        case "AUD":
          conversionNames[2].setShorty("$");
          break;
        case "RUB":
          conversionNames[2].setShorty("р");
          break;
        case "CHF":
          conversionNames[2].setShorty("Fr");
          break;
        case "CAD":
          conversionNames[2].setShorty("$");
          break;
        case "JPY":
          conversionNames[2].setShorty("¥");
          break;
        default:
          conversionNames[2].setShorty(currency);
          break;
      }
    }

    //Log.d("updateingn", "Initialize price update");
    EtherscanAPI.getInstance().getEtherPrice(new Callback() {
      @Override public void onFailure(Call call, IOException e) {
      }

      @Override public void onResponse(Call call, final Response response) throws IOException {
        try {
          JSONObject data = new JSONObject(response.body().string()).getJSONObject("result");

          conversionNames[1].setRate(data.getDouble("ethbtc"));
          conversionNames[2].setRate(data.getDouble("ethusd"));
          if (!currency.equals("USD")) {
            convert(currency, update);
          } else {
            update.onUpdate(response);
          }

        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
  }

  private void convert(final String currency, final NetworkUpdateListener update) throws IOException {
    EtherscanAPI.getInstance().getPriceConversionRates("USD" + currency, new Callback() {
      @Override public void onFailure(Call call, IOException e) {
      }

      @Override public void onResponse(Call call, final Response response) throws IOException {
        rateForChartDisplay = ResponseParser.parsePriceConversionRate(response.body().string());
        conversionNames[2].setRate(Math.floor(conversionNames[2].getRate() * rateForChartDisplay * 100) / 100);
        update.onUpdate(response);
      }
    });
  }

}
