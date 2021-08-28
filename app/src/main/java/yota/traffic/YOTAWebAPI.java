package yota.traffic;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class YOTAWebAPI {
    //  URLs
    private final String APIBase = "https://webapi.yota.ru/";
    private final String LoginBase = "https://login.yota.ru/";
    private final String MyBase = "https://my.yota.ru/";

    //  Messages
    private final String msgExNullAlias = "Не указан логин!";
    private final String msgExEncoding = "Обнаружена неподдерживаемая кодировка!";
    private final String msgExNullPw = "Не указан пароль!";
    private final String msgExNullId = "Необходимый параметр ответа сервера 'customerId' пуст!";
    private final String msgExUserNotFound = "Такого пользователя не существует!";
    private final String msgExUserWrongOrPw = "Неверный логин и/или пароль!";
    private final String msgExLimitError = "Превышен лимит запросов! Попробуйте повторить позже.";
    private final String msgExNullAliasAndPw = "Не указан логин и пароль!";
    private final String msgExNullAliasOrCaptcha = "Не указан логин или капча!";
    private final String msgExNullOfferData = "Не указаны необходимые данные для выполнения запроса!";
    private final String msgExWrongResponse = "Неожиданный ответ от сервера!";
    private final String msgExTechnicalDifficulties = "В данный момент Личный кабинет перегружен или\nв нем проводятся сервисные работы.";
    private final String msgExPaymentFailed = "При оплате произошла ошибка, списания денег с вашего счёта не произошло.\nУбедитесь, что введённые вами данные верны и повторите попытку позднее.";

    //  Tags
    private final String TAG = "YOTAWebAPI";
    private final String userAgent = "YOTA Регулятор";

    //  Cookies
    private Map loginCookies;
    private Map paymentCookies;

    //  Forms
    private Map<String, String> offerData = new HashMap<>();

    //  Booleans
    private boolean bLoggedIn = false;

    //  Ids
    private long customerId;

    private JSONObject checkCustomerExist(final String alias) throws HttpStatusException {
        if (alias.isEmpty())
            throw new NullPointerException(msgExNullAlias);

        //  1st request to figure out IDToken1.
        boolean bLoaded = false;
        String response = null;
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(APIBase + "webapi-3.3/customers?op=checkCustomerExist&alias=" + URLEncoder.encode(alias.trim(), "utf-8"));
                connection.userAgent(userAgent);
                connection.header("X-Originator", "WSC");
                connection.header("X-OriginatorComponent", "loft_login_widget");
                connection.header("X-Request-Source", "loft_login_widget");
                connection.header("X-Request-Source-Version", "4.0");
                connection.header("X-System-Id", "openportal");
                connection.header("Origin", "https://widgets.yota.ru");
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.method(Connection.Method.POST);
                connection.data("{}");
                connection.execute();

                response = connection.response().body();

                if (connection.response().statusCode() == 404)
                    throw new HttpStatusException(msgExUserNotFound, connection.response().statusCode(), APIBase + "webapi-3.3/customers?op=checkCustomerExist&alias=" + URLEncoder.encode(alias, "utf-8"));
                if (connection.response().statusCode() == 503)
                    throw new HttpStatusException(msgExTechnicalDifficulties, connection.response().statusCode(), APIBase + "webapi-3.3/customers?op=checkCustomerExist&alias=" + URLEncoder.encode(alias, "utf-8"));

                bLoaded = true;
            }catch (HttpStatusException e) {
                throw new HttpStatusException(e.getMessage(), e.getStatusCode(), e.getUrl());
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        Log.v(TAG, "[checkCustomerExist] First response = " + response);

        //  Build up a JSON object from what we have in response.
        JSONObject json = null;
        try {
            json = new JSONObject(response);

            customerId = Long.parseLong(json.get("externalId").toString());
            Log.v(TAG, "[checkCustomerExist] JSON[externalId] = " + json.get("externalId"));
        }catch (JSONException e) {
            e.printStackTrace();
        }

        //  CustomerID is ready.
        return json;
    }

    private boolean UILogin(final String alias, final String pw) throws JSONException, NullPointerException, HttpStatusException {
        if (alias.isEmpty() && pw.isEmpty())
            throw new NullPointerException(msgExNullAliasAndPw);
        if (alias.isEmpty())
            throw new NullPointerException(msgExNullAlias);
        if (pw.isEmpty())
            throw new NullPointerException(msgExNullPw);

        //  Do the @checkCustomerExist first, then proceed to login.
        JSONObject json = checkCustomerExist(alias);

        if (json.isNull("externalId"))
            throw new JSONException(msgExNullId);

        //  2nd request - process login data.
        boolean bLoaded = false;
        int responseCode = 0;
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(LoginBase + "UI/Login");
                connection.userAgent(userAgent);
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.method(Connection.Method.POST);

                connection.data("org", "customer");
                connection.data("ForceAuth", "true");
                connection.data("source", "loft_login_widget");
                connection.data("IDToken1", json.get("externalId").toString());
                connection.data("IDToken2", pw);
                connection.data("gotoOnFail", "https://widgets.yota.ru/wrs/gadgets/ifr?renderType=iframe");
                connection.data("mid", "0");
                connection.data("country", "RU");
                connection.data("lang", "ru_ru");
                connection.data("nocache", "1");
                connection.data("url", "https://widgets.yota.ru/widgets/login/login.xml");
                connection.data("up_error", "1");

                connection.execute();

                responseCode = connection.response().statusCode();

                if (responseCode == 400)
                    throw new HttpStatusException(msgExUserWrongOrPw, responseCode, LoginBase + "UI/Login");
                if (connection.response().cookies().size() == 0)
                    throw new HttpStatusException(msgExLimitError, responseCode, LoginBase + "UI/Login");

                loginCookies = connection.response().cookies();

                Log.v(TAG, "[UILogin] ResponseCode = " + responseCode);
                Log.v(TAG, "[UILogin] ResponseText = " + connection.response().body());
                Log.v(TAG, "[UILogin] Cookies count = " + loginCookies.size());

                bLoaded = true;
            }catch (HttpStatusException e) {
                    throw new HttpStatusException(e.getMessage(), e.getStatusCode(), e.getUrl());
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        return responseCode == 302 || responseCode == 200;
    }

    private void loginSuccess() {
        boolean bLoaded = false;
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(MyBase + "selfcare/loginSuccess");
                connection.userAgent(userAgent);
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.followRedirects(false);
                connection.cookies(loginCookies);
                connection.method(Connection.Method.GET);
                connection.execute();

                loginCookies.remove("JSESSIONID");
                loginCookies.put("JSESSIONID", connection.response().cookie("JSESSIONID"));

                Log.v(TAG, "[loginSuccess] ResponseCode = " + connection.response().statusCode());
                Log.v(TAG, "[loginSuccess] Redirect to " + connection.response().header("Location"));

                bLoaded = true;
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void restorePassword(final String alias, final String captcha) throws NullPointerException {
        if (alias.isEmpty() || captcha.isEmpty())
            throw new NullPointerException(msgExNullAliasOrCaptcha);

        boolean bloaded = false;
        while (!bloaded) {
            try {
                Connection.Response response = Jsoup.connect(MyBase + "/selfcare/restorePassword/sendCheckCode")
                        .userAgent(userAgent)
                        .data("alias", alias)
                        .data("captcha", captcha)
                        .cookies(loginCookies)
                        .method(Connection.Method.POST)
                        .execute();

                bloaded = true;
            }catch (SocketTimeoutException e) {
                bloaded = false;
            }catch (IOException e) {
                bloaded = false;
            }
        }
    }

    public void changeCurrentOffer(Map<String, String> data) throws NullPointerException, HttpStatusException {
        if (!data.containsKey("product") || !data.containsKey("offerCode") || !data.containsKey("productOfferingCode"))
            throw new NullPointerException(msgExNullOfferData);

        boolean bLoaded = false;
        int responseCode = 0;
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(MyBase + "selfcare/devices/changeOffer");
                connection.userAgent(userAgent);
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.cookies(loginCookies);
                connection.method(Connection.Method.POST);

                for (Map.Entry<String, String> entry : data.entrySet())
                    connection.data(entry.getKey(), entry.getValue());

                connection.execute();

                responseCode = connection.response().statusCode();

                Log.v(TAG, "[changeCurrentOffer] ResponseCode = " + responseCode);

                if (responseCode == 404 || responseCode == 400)
                    throw new HttpStatusException(msgExWrongResponse, responseCode, MyBase + "selfcare/devices/changeOffer");

                bLoaded = true;
            }catch (HttpStatusException e) {
                throw new HttpStatusException(e.getMessage(), e.getStatusCode(), e.getUrl());
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void login(final String email, final String password) throws JSONException, NullPointerException, HttpStatusException {
        try {
            if (UILogin(email, password)) {
                loginSuccess();
                bLoggedIn = true;
            }else
                bLoggedIn = false;
        }catch (JSONException e) {
            bLoggedIn = false;
            e.printStackTrace();
            throw new JSONException(e.getMessage());
        }catch (NullPointerException e) {
            bLoggedIn = false;
            e.printStackTrace();
            throw new NullPointerException(e.getMessage());
        }catch (HttpStatusException e) {
            bLoggedIn  = false;
            e.printStackTrace();
            throw new HttpStatusException(e.getMessage(), e.getStatusCode(), e.getUrl());
        }
    }

    public boolean isLoggedIn() {
        return bLoggedIn;
    }

    public JSONObject getServiceOffers() {
        boolean bLoaded = false;
        Document document = new Document("");
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(MyBase + "selfcare/devices");
                connection.userAgent(userAgent);
                connection.cookies(loginCookies);
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.method(Connection.Method.GET);

                connection.execute();

                document = connection.response().parse();

                bLoaded = true;
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        //  Get services.
        for (Element form_el : document.select("#sliders > dl > dd > div > div.devices-list-wrapper > div > div.slider-type-device-wrapper > div > form > input"))
            offerData.put(form_el.attr("name"), form_el.attr("value"));
        offerData.put("isDisablingAutoprolong", "false");
        String product = document.select("#sliders > dl > dd > div > div.devices-list-wrapper > div > div.slider-type-device-wrapper > div > form > input:nth-child(1)").attr("value");
        String daysleft = document.select("#sliders > dl > dd > div > div.devices-list-wrapper > div > div.slider-type-device-wrapper > div > form > div.hint.hint_pos_current-conditions > div > div.content-container > div > div > div.time > strong").text().trim();
        String daysleft_term = document.select("#sliders > dl > dd > div > div.devices-list-wrapper > div > div.slider-type-device-wrapper > div > form > div.hint.hint_pos_current-conditions > div > div.content-container > div > div > div.time > span").text().trim().replace("осталось", "");
        String json = document.select("#sliders > dl > dd > div > div.devices-list-wrapper > div > script:nth-child(1)").html();
        json = json.substring(json.indexOf("=") + 2);
        json = json.substring(0, json.lastIndexOf("}};") + 2);

        try {
            return new JSONObject(json).getJSONObject(product).put("daysleft", daysleft).put("daysleft_term", daysleft_term);
        }catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public int getBalance() {
        boolean bLoaded = false;
        Document document = new Document("");
        while (!bLoaded) {
            try {
                Connection connection = Jsoup.connect(MyBase + "selfcare/devices");
                connection.userAgent("YOTA Регулятор");
                connection.cookies(loginCookies);
                connection.ignoreContentType(true);
                connection.ignoreHttpErrors(true);
                connection.method(Connection.Method.GET);

                connection.execute();

                document = connection.response().parse();

                bLoaded = true;
            }catch (SocketTimeoutException e) {
                bLoaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        //  Get balance.
        return Integer.parseInt(document.select("dd#balance-holder > span").text().trim());
    }

    public Map getOfferData() { return offerData; }

    public Map getDepositForm(int depositSum) throws NullPointerException {

        if (customerId == 0)
            throw new NullPointerException(msgExNullId);

        boolean bloaded = false;
        Document document = new Document("");
        while (!bloaded) {
            try {
                Connection.Response response = Jsoup.connect(MyBase + "payments/cards/card_redirect.jsp")
                        .followRedirects(false)
                        .userAgent(userAgent)
                        .cookies(loginCookies)
                        .data("amount", Integer.toString(depositSum))
                        .data("card_type", "VISA")
                        .data("user_id", Long.toString(customerId))
                        .data("operation", "TopUp")
                        .data("source_of_changes", "SC")
                        .data("card_id", "")
                        .data("success_url", "https://my.yota.ru/selfcare/profile")
                        .data("decline_url", "https://my.yota.ru/selfcare/payment/failed")
                        .method(Connection.Method.POST)
                        .execute();

                bloaded = true;
                document = response.parse();
            }catch (SocketTimeoutException e) {
                bloaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        //  Response caught up. Retrieve necessary info and process payment.
        Map data = new HashMap();
        Elements form_inputs = document.select("form > input");
        String redirect_url = document.select("form").attr("action");
        if (form_inputs.isEmpty() || redirect_url == null)
            throw new NullPointerException(msgExWrongResponse);

        for (Element input : form_inputs)
            data.put(input.attr("name"), input.attr("value"));
        data.put("temp", redirect_url);

        //  All data ready.

        return data;
    }

    public void processPayment(Map depositFormData, Map cardData) {
        //  Step 1. Retrieve url necessary for payment.
        if (depositFormData == null ||
                cardData == null)
            throw new NullPointerException(msgExNullOfferData);

        boolean bloaded = false;
        String redirect_url = depositFormData.get("temp").toString();
        depositFormData.remove("temp");
        while (!bloaded) {
            try {
                Connection.Response response = Jsoup.connect(redirect_url)
                        .userAgent(userAgent)
                        .data(depositFormData)
                        .followRedirects(false)
                        .method(Connection.Method.POST)
                        .execute();

                //  Redirect link is ready. Opening it will generate cookies and redirect to payment page.
                Log.v(TAG, "[processPayment] Response code " + response.statusCode() + ", url - " + response.header("location"));

                //  Step 2. Redirect to payment page.
                Connection.Response response_second = Jsoup.connect(response.header("location"))
                        .userAgent(userAgent)
                        .referrer(MyBase + "payments/cards/card_redirect.jsp")
                        .followRedirects(false)
                        .execute();

                Log.v(TAG, "[processPayment] Response code " + response_second.statusCode() + ", url - " + response_second.header("location"));
                paymentCookies = response_second.cookies();

                //  eshop.xml
                Connection.Response response_third = Jsoup.connect(response_second.header("location"))
                        .userAgent(userAgent)
                        .followRedirects(false)
                        .method(Connection.Method.GET)
                        .cookies(paymentCookies)
                        .execute();

                Log.v(TAG, "[processPayment] Response code " + response_third.statusCode() + ", title - " + response_third.parse().title());
                paymentCookies = response_third.cookies();

                //  Step 3. Retrieve payment page inputs and form action url.
                Elements payment_form_elements = response_third.parse().select("form > input");

                if (payment_form_elements.isEmpty())
                    throw new NullPointerException(msgExWrongResponse);

                Map<String, String> data_ = new HashMap<>();
                for (Element input : payment_form_elements) {
                    Log.v(TAG, input.attr("name") + " = " + input.attr("value"));
                    data_.put(input.attr("name"), input.attr("value"));
                }

                String action_url = response_third.parse().select("form").attr("action").trim();
                if (action_url == null)
                    throw new NullPointerException(msgExWrongResponse);

                data_.put("skr_month", cardData.get("skr_month").toString());
                data_.put("skr_year", cardData.get("skr_year").toString());
                data_.put("skr_fio", cardData.get("skr_fio").toString());
                data_.put("skr_cardCvc", cardData.get("skr_cardCvc").toString());
                data_.put("cps_email", depositFormData.get("email").toString());
                data_.put("skr_card-number", cardData.get("skr_card-number").toString());

                //  Step 4. Process payment because all data is ready.
                Connection.Response response_payment = Jsoup.connect(action_url)
                        .userAgent(userAgent)
                        .followRedirects(false)
                        .data(data_)
                        .method(Connection.Method.POST)
                        .cookies(paymentCookies)
                        .execute();

                Log.v(TAG, "[processPayment] Response code " + response_payment.statusCode() + ", title - " + response_payment.parse().title());
                if (response_payment.statusCode() == 302)
                    Log.v(TAG, "[processPayment] Response redirect - " + response_payment.header("location"));

                if (response_payment.header("location").contains("/failed?"))
                    throw new NullPointerException(msgExPaymentFailed);
                if (response_payment.header("location").contains("paymentresult.xml"))
                    return;

                bloaded = true;
            }catch (SocketTimeoutException e) {
                bloaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
