package yota.traffic;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.w3c.dom.Text;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class Regulator extends AppCompatActivity {

    protected final YOTAWebAPI api = new YOTAWebAPI();
    private Context context;
    private String preferencesName = "YOTARegulator";
    private boolean secondScreen = false;
    private JSONObject servicesJSON;
    private int currentProgress = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_regulator);

        this.context = Regulator.this;

        SharedPreferences preferences = getSharedPreferences(preferencesName, MODE_PRIVATE);

        if (!preferences.getBoolean("remember", false)) {
            //  Галка не стоит либо нету записи в настройках.
            showLoginDialog(false);
            return;
        }

        AsyncLoginTask task = new AsyncLoginTask();
        task.execute(preferences.getString("login", ""), preferences.getString("password", ""), Boolean.toString(preferences.getBoolean("remember", false)));

        ImageView ivDeposit = (ImageView)findViewById(R.id.ivDeposit);

        ivDeposit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("YOTARegulator", "Click on deposit.");

                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                final View payment_dialog_view = View.inflate(context, R.layout.payment_dialog, null);
                ((EditText)(payment_dialog_view.findViewById(R.id.etCardNumber))).addTextChangedListener(new FourDigitCardFormatWatcher());
                ((EditText)(payment_dialog_view.findViewById(R.id.etCardHolder))).setFilters(new InputFilter[] { new InputFilter.AllCaps() });
                ((Button)(payment_dialog_view.findViewById(R.id.btnPay))).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText etCardNumber = (EditText)payment_dialog_view.findViewById(R.id.etCardNumber);
                        EditText etCardMonth = (EditText)payment_dialog_view.findViewById(R.id.etCardMonth);
                        EditText etCardYear = (EditText)payment_dialog_view.findViewById(R.id.etCardYear);
                        EditText etCardHolder = (EditText)payment_dialog_view.findViewById(R.id.etCardHolder);
                        EditText etCardSecurityNumber = (EditText)payment_dialog_view.findViewById(R.id.etCardSecurityNumber);
                        EditText etAmount = (EditText)payment_dialog_view.findViewById(R.id.etAmount);

                        Log.v("YOTARegulator", "[paymentDialog] check field 'cardnumber', length expected >= 16, found " + etCardNumber.getText().toString().length());
                        Log.v("YOTARegulator", "[paymentDialog] check field 'cardmonth', length expected 2, found " + etCardMonth.getText().toString().length());
                        Log.v("YOTARegulator", "[paymentDialog] check field 'cardyear', length expected 2, found " + etCardYear.getText().toString().length());
                        Log.v("YOTARegulator", "[paymentDialog] check field 'cardholder', length expected >= 9, found " + etCardHolder.getText().toString().length());
                        Log.v("YOTARegulator", "[paymentDialog] check field 'cvc', length expected 3, found " + etCardSecurityNumber.getText().toString().length());
                        Log.v("YOTARegulator", "[paymentDialog] check field 'amount', length exptected > 0, found " + etAmount.getText().toString().length());

                        //  Проверить правильность ввода всех полей.
                        if (etCardNumber.getText().toString().length() < 16 ||
                                etCardMonth.getText().toString().length() != 2 ||
                                etCardYear.getText().toString().length() != 2 ||
                                etCardHolder.getText().toString().length() < 9 ||
                                etCardSecurityNumber.getText().toString().length() != 3 ||
                                etAmount.getText().toString().length() == 0) {
                            Toast.makeText(context, "Проверьте правильность ввода всех данных и повторите попытку.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        final int depositAmount = Integer.parseInt(etAmount.getText().toString());
                        Map _cardData = new HashMap();
                        _cardData.put("skr_month", etCardMonth.getText().toString());
                        _cardData.put("skr_year", etCardYear.getText().toString());
                        _cardData.put("skr_fio", etCardHolder.getText().toString());
                        _cardData.put("skr_cardCvc", etCardSecurityNumber.getText().toString());
                        _cardData.put("skr_card-number", etCardNumber.getText().toString().replace(" ", ""));

                        final Map cardData = new HashMap(_cardData);

                        final ProgressDialog dialog = new ProgressDialog(context);
                        dialog.setIndeterminate(true);
                        dialog.setCancelable(false);
                        dialog.setMessage("Пополнение баланса...");
                        dialog.show();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Map formData = api.getDepositForm(depositAmount);
                                    api.processPayment(formData, cardData);

                                    ((Activity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialog.dismiss();

                                            //  Показать окно успешной оплаты.
                                            AlertDialog dialog = new AlertDialog.Builder(context)
                                                    .setCancelable(false)
                                                    .setMessage("Оплата успешно завершена!")
                                                    .setPositiveButton("ОК", null)
                                                    .create();

                                            dialog.show();
                                        }
                                    });
                                }catch (NullPointerException e) {
                                    e.printStackTrace();

                                    final String message = e.getMessage();

                                    //  Показать окно с неуспешной оплатой.
                                    ((Activity)context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialog.dismiss();

                                            AlertDialog dialog = new AlertDialog.Builder(context)
                                                    .setCancelable(false)
                                                    .setMessage("Оплата завершилась неудачей. Ошибка:\n" + message)
                                                    .setPositiveButton("ОК", null)
                                                    .create();

                                            dialog.show();
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                });

                builder.setView(payment_dialog_view);
                builder.setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });

                builder.create().show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (secondScreen) {
            setContentView(R.layout.activity_regulator);
            new AsyncDataLoadTask().execute();
            secondScreen = false;
        } else
            super.onBackPressed();
    }

    protected void showLoginDialog(boolean bSkipAuth) {
        final LayoutInflater inflater = ((Activity) context).getLayoutInflater();
        final View view_dialog_login = inflater.inflate(R.layout.dialog_login, null);

        SharedPreferences preferences = getSharedPreferences(preferencesName, MODE_PRIVATE);
        ((CheckBox) view_dialog_login.findViewById(R.id.chkRemember)).setChecked(preferences.getBoolean("remember", false));
        ((EditText) view_dialog_login.findViewById(R.id.etLogin)).setText(preferences.getString("login", ""));
        ((EditText) view_dialog_login.findViewById(R.id.etPassword)).setText(preferences.getString("password", ""));

        if (bSkipAuth) {
            AsyncLoginTask task = new AsyncLoginTask();
            task.execute(preferences.getString("login", ""), preferences.getString("password", ""), preferences.getBoolean("remember", false) ? "true" : "false");

            return;
        }

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);
        builder.setView(view_dialog_login)
                .setCancelable(false)
                .setPositiveButton("Войти", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String login = ((EditText) view_dialog_login.findViewById(R.id.etLogin)).getText().toString();
                        String password = ((EditText) view_dialog_login.findViewById(R.id.etPassword)).getText().toString();
                        String bremember = Boolean.toString(((CheckBox) view_dialog_login.findViewById(R.id.chkRemember)).isChecked());

                        AsyncLoginTask task = new AsyncLoginTask();
                        task.execute(login, password, bremember);
                    }
                });

        builder.create().show();
    }

    public class AsyncDataLoadTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progressDialog;
        private int balance;
        private JSONObject json;

        private String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Загружаются данные...");
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            json = api.getServiceOffers();
            balance = api.getBalance();

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            servicesJSON = json;

            TextView tvAccount = (TextView) findViewById(R.id.tvAccount);
            TextView tvMonthlyPayment = (TextView) findViewById(R.id.tvMonthlyPayment);
            TextView tvPaymentNext = (TextView) findViewById(R.id.tvPaymentNext);
            TextView tvCurrentSpeed = (TextView) findViewById(R.id.tvCurrentSpeed);
            TextView tvCurrentSpeedTerm = (TextView) findViewById(R.id.tvCurrentSpeedTerm);
            Calendar calendar = Calendar.getInstance();

            //  Отображаем загруженные данные.
            try {
                tvAccount.setText("Баланс " + Integer.toString(balance) + " руб");

                tvMonthlyPayment.setText(json.getJSONObject("currentProduct").getString("amountNumber") + " Р");

                String term = json.getString("daysleft_term").trim().replaceAll("\u00a0", "");
                Log.v("YOTARegulator", "Left amount: " + json.getString("daysleft") + " " + term + ".");
                Log.v("YOTARegulator", "Days in month: " + calendar.getActualMaximum(Calendar.DAY_OF_MONTH) + "; Next date: " + (calendar.get(Calendar.DAY_OF_MONTH) + Integer.valueOf(json.getString("daysleft"))));
                if (term.equals("часов") || term.equals("часа") || term.equals("час"))
                    tvPaymentNext.setText("Следующее списание через " + json.getString("daysleft") + " " + json.getString("daysleft_term"));
                else
                    //  Sanity check. Проверяем, сколько в этом месяце дней и учитываем это при отображении даты следующего списания.
                    if (calendar.get(Calendar.DAY_OF_MONTH) + Integer.valueOf(json.getString("daysleft")) > calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                        calendar.add(Calendar.DAY_OF_MONTH, Integer.valueOf(json.getString("daysleft")));
                        tvPaymentNext.setText("Следующее списание " + calendar.get(Calendar.DAY_OF_MONTH) + " " + months[calendar.get(Calendar.MONTH)]);
                    }else
                        tvPaymentNext.setText("Следующее списание " + (calendar.get(Calendar.DAY_OF_MONTH) + Integer.valueOf(json.getString("daysleft"))) + " " + months[calendar.get(Calendar.MONTH)]);

                String speedNumber = json.getJSONObject("currentProduct").getString("speedNumber");
                String speedTerm = json.getJSONObject("currentProduct").getString("speedString");
                if (speedTerm.startsWith("Мбит")) {
                    tvCurrentSpeed.setText(speedNumber);
                    tvCurrentSpeedTerm.setText("Мб/с");
                }
                if (speedTerm.startsWith("Кбит")) {
                    tvCurrentSpeed.setText(speedNumber);
                    tvCurrentSpeedTerm.setText("Кб/с");
                }
                if (speedNumber.startsWith("<div")) {
                    tvCurrentSpeed.setText("");
                    tvCurrentSpeedTerm.setText("макcимальная");
                }
            } catch (JSONException | NullPointerException e) {
                e.printStackTrace();
            }

            TextView tvLinkChangeOffer = (TextView) findViewById(R.id.tvLinkChangeOffer);
            tvLinkChangeOffer.setOnClickListener(new ChangeOfferClick());

            progressDialog.dismiss();

            //  Проверяем на наличие обновлений в первую очередь.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    new Updater(context).Update();
                }
            }).start();
        }
    }

    public class AsyncLoginTask extends AsyncTask<String, Void, Boolean> {

        private ProgressDialog dialogProgress;
        private boolean bError = false;
        private String sError;

        private Object[] returnData = new Object[3];

        @Override
        protected void onPreExecute() {
            dialogProgress = new ProgressDialog(context);
            dialogProgress.setIndeterminate(true);
            dialogProgress.setCancelable(false);
            dialogProgress.setMessage("Выполняется вход...");
            dialogProgress.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            returnData[0] = params[0];
            returnData[1] = params[1];
            returnData[2] = params[2];

            try {
                api.login(params[0], params[1]);

                return true;
            } catch (NullPointerException e) {
                sError = e.getMessage();
                bError = true;
            } catch (JSONException e) {
                sError = e.getMessage();
                bError = true;
                e.printStackTrace();
            } catch (HttpStatusException e) {
                sError = e.getMessage();
                bError = true;
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            dialogProgress.dismiss();

            if (bError) {
                //  Показать диалог с ошибкой.
                android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);

                builder.setTitle("Ошибка");
                builder.setMessage("При входе произошла ошибка:\n" + sError);
                builder.setCancelable(false);

                if (sError.startsWith("Превышен")) {
                    builder.setNeutralButton("Отмена", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            showLoginDialog(false);
                        }
                    });
                    builder.setPositiveButton("Повтор", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            showLoginDialog(true);
                        }
                    });
                } else {
                    builder.setNeutralButton("ОК", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            showLoginDialog(false);
                        }
                    });
                }

                builder.create().show();
            }

            ((Regulator) context).onLoginCompleted(result, returnData);
        }
    }

    private void onLoginCompleted(Boolean result, Object[] returnData) {
        Log.v(preferencesName, "onLoginCompleted: " + returnData[0].toString() + ", " + returnData[1].toString() + ", " + returnData[2].toString());
        //  Записываем данные в настройки.
        SharedPreferences preferences = getSharedPreferences(preferencesName, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("remember", returnData[2].toString() == "true");
        editor.putString("login", returnData[0].toString().trim());
        editor.putString("password", returnData[1].toString().trim());

        editor.apply();

        if (!result)
            return;

        //  Вход успешен, запускаем загрузку данных.
        new AsyncDataLoadTask().execute();
    }

    public class ChangeOfferClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            secondScreen = true;
            setContentView(R.layout.activity_regulator_changer);

            //  Узнаём баланс.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final int balance = api.getBalance();

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView tvChangeAccount = (TextView)findViewById(R.id.tvChangeAccount);
                            tvChangeAccount.setText("Баланс " + balance + " руб.");
                        }
                    });
                }
            }).start();

            SeekBar sbChange = (SeekBar) findViewById(R.id.sbChange);

            try {
                for (int i = 0; i < servicesJSON.getJSONArray("steps").length(); i++) {
                    Log.v("YOTARegulator", servicesJSON.getJSONObject("currentProduct").getString("speedNumber") + "; " + servicesJSON.getJSONArray("steps").getJSONObject(i).getString("speedNumber"));
                    if (servicesJSON.getJSONObject("currentProduct").getString("speedNumber").equals(servicesJSON.getJSONArray("steps").getJSONObject(i).getString("speedNumber")))
                        currentProgress = i;
                }

                Log.v("YOTARegulator", "PROGRESS: " + currentProgress);
                sbChange.setProgress(0);
                sbChange.setMax(servicesJSON.getJSONArray("steps").length() - 1);
                sbChange.setProgress(currentProgress);
                updateOffer(currentProgress);

            } catch (JSONException e) {
                e.printStackTrace();
            }

            sbChange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                    updateOffer(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        }
    }

    public void updateOffer(int progress) {
        final TextView tvChangeLink = (TextView) findViewById(R.id.tvChangeLink);
        final int progress_ = progress;

        tvChangeLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("YOTARegulator", "curr = " + currentProgress + "; progress = " + progress_);

                if (currentProgress == progress_)
                    return;

                new AsyncChangeOfferTask().execute(progress_);
                currentProgress = progress_;

                tvChangeLink.setBackgroundColor(Color.TRANSPARENT);
                tvChangeLink.setTextColor(Color.parseColor("#4A4A4A"));
                tvChangeLink.setPadding(0, 0, 0, 0);
                tvChangeLink.setText("Текущие условия");
            }
        });

        if (progress != currentProgress) {
            tvChangeLink.setBackgroundColor(Color.parseColor("#00ADEF"));
            tvChangeLink.setTextColor(Color.WHITE);
            tvChangeLink.setPadding(18, 10, 18, 10);
            tvChangeLink.setText("Покдлючить");
        }else{
            tvChangeLink.setBackgroundColor(Color.TRANSPARENT);
            tvChangeLink.setTextColor(Color.parseColor("#4A4A4A"));
            tvChangeLink.setPadding(0, 0, 0, 0);
            tvChangeLink.setText("Текущие условия");
        }

        TextView tvChangeMonthlyPayment = (TextView) findViewById(R.id.tvChangeMonthlyPayment);
        TextView tvChangeNewSpeed = (TextView) findViewById(R.id.tvChangeNewSpeed);
        TextView tvChangeDaysLeft = (TextView) findViewById(R.id.tvChangeDaysLeft);
        TextView tvChangeDaysLeftText = (TextView) findViewById(R.id.tvChangeDaysLeftText);

        try {
            tvChangeMonthlyPayment.setText(servicesJSON.getJSONArray("steps").getJSONObject(progress_).getString("amountNumber") + " Р");

            String speedString = servicesJSON.getJSONArray("steps").getJSONObject(progress_).getString("speedString");
            String speedNumber = servicesJSON.getJSONArray("steps").getJSONObject(progress_).getString("speedNumber");

            if (speedString.startsWith("Мбит"))
                tvChangeNewSpeed.setText("до " + speedNumber + " Мб/сек");
            if (speedString.startsWith("Кбит"))
                tvChangeNewSpeed.setText("до " + speedNumber + " Кб/сек");
            if (speedNumber.startsWith("<div"))
                tvChangeNewSpeed.setText("максимальная скорость");

            tvChangeDaysLeft.setText(servicesJSON.getJSONArray("steps").getJSONObject(progress_).getString("remainNumber"));
            tvChangeDaysLeftText.setText(servicesJSON.getJSONArray("steps").getJSONObject(progress_).getString("remainString") + "\nосталось");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class AsyncChangeOfferTask extends AsyncTask<Integer, Void, Void> {

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);

            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setMessage("Применение...");

            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Integer... params) {
            try {
                Map offerData = api.getOfferData();

                offerData.remove("offerCode");
                offerData.remove("pimpaPosition");
                offerData.remove("productOfferingCode");

                offerData.put("pimpaPosition", servicesJSON.getJSONArray("steps").getJSONObject(params[0]).getString("position"));
                offerData.put("offerCode", servicesJSON.getJSONArray("steps").getJSONObject(params[0]).getString("code"));
                offerData.put("productOfferingCode", offerData.get("offerCode"));

                api.changeCurrentOffer(offerData);
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (HttpStatusException e) {
                Log.v("YOTARegulator", e.getStatusCode() + ": " + e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
        }
    }
}
