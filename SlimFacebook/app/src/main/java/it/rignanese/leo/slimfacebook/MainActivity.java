package it.rignanese.leo.slimfacebook;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.rignanese.leo.slimfacebook.settings.SettingsActivity;
import it.rignanese.leo.slimfacebook.utility.Dimension;
import it.rignanese.leo.slimfacebook.utility.MyAdvancedWebView;

import static it.rignanese.leo.slimfacebook.R.id.center;
import static it.rignanese.leo.slimfacebook.R.id.webView;

/**
 * SlimSocial for Facebook is an Open Source app realized by Leonardo Rignanese <rignanese.leo@gmail.com>
 * GNU GENERAL PUBLIC LICENSE  Version 2, June 1991
 * GITHUB: https://github.com/rignaneseleo/SlimSocial-for-Facebook
 */
public class MainActivity extends AppCompatActivity implements MyAdvancedWebView.Listener {

    private SwipeRefreshLayout swipeRefreshLayout;//the layout that allows the swipe refresh
    private MyAdvancedWebView webViewFacebook;//the main webView where is shown facebook
    private WebViewClient webViewClient;
    private SharedPreferences savedPreferences;//contains all the values of saved preferences

    private boolean noConnectionError = false;//flag: is true if there is a connection error. It should reload the last useful page

    private boolean isSharer = false;//flag: true if the app is called from sharer
    private String urlSharer = "";//to save the url got from the sharer

    // create link handler (long clicked links)
    private final MyHandler linkHandler = new MyHandler(this);

    //full screen video variables
    private FrameLayout mTargetView;
    private WebChromeClient myWebChromeClient;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private View mCustomView;
    private Menu menuBar;
    private int alphaSelected = 255;
    private int alphaNotSelected = 127;
    private String fbMobileNewsfeedUrl;
    private String fbMobileFriendsUrl;
    private String fbMobileMessageUrl;
    private String fbMobileNotificationsUrl;
    private String fbMobileSearchUrl;
    private String fbMobileBookmarksUrl;
    private HashMap<String, Integer> notificationStates = new HashMap<String, Integer>();


    //*********************** ACTIVITY EVENTS ****************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fbMobileNewsfeedUrl = getString(R.string.urlFacebookMobile);
        fbMobileFriendsUrl = getString(R.string.urlFacebookMobileFriends);
        fbMobileMessageUrl = getString(R.string.urlFacebookMobileMessages);
        fbMobileNotificationsUrl = getString(R.string.urlFacebookMobileNotifications);
        fbMobileSearchUrl = getString(R.string.urlFacebookMobileSearch);
        fbMobileBookmarksUrl = getString(R.string.urlFacebookMobileBookmarks);
        savedPreferences = PreferenceManager.getDefaultSharedPreferences(this); // setup the sharedPreferences

        SetTheme();//set the activity theme

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getActionBar().hide();
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_container);
        int actionBarHeight = getSupportActionBar().getHeight();
        ConstraintLayout.LayoutParams layoutParams = ((ConstraintLayout.LayoutParams) appBarLayout.getLayoutParams());
        layoutParams.bottomToTop = swipeRefreshLayout.getId();
        appBarLayout.setLayoutParams(layoutParams);
        //swipeRefreshLayout.setPadding(0, Dimension.heightForFixedFacebookNavbar(getApplicationContext()),0,0);
        // if the app is being launched for the first time
        //swipeRefreshLayout.setSize(swipeRefreshLayout.getHeight()+88);
        //swipeRefreshLayout.setTranslationY(-88);
        MyAdvancedWebView myAdvancedWebView = findViewById(webView);
        if (savedPreferences.getBoolean("first_run", true)) {
            savedPreferences.edit().putBoolean("first_run", false).apply();
        }


        SetupRefreshLayout();// setup the refresh layout

        ShareLinkHandler();//handle a link shared (if there is)

        SetupWebView();//setup webview
        SetupWebViewClient();//set WebClient to be able to intercept traffic and make direct injections into css
        SetupFullScreenVideo();

        SetupOnLongClickListener();
        SetupWebViewClient();
        if (isSharer) {//if is a share request
            Log.d("MainActivity.OnCreate", "Loading shared link");
            webViewFacebook.loadUrl(urlSharer);//load the sharer url
            isSharer = false;
        } else if (getIntent() != null && getIntent().getDataString() != null) {
            //if the app is opened by fb link
            webViewFacebook.loadUrl(FromDesktopToMobileUrl(getIntent().getDataString()));
        } else GoHome();//load homepage
        initNotificationStates();
    }

    private void initNotificationStates() {
        this.notificationStates.put("feed", 0);
        this.notificationStates.put("requests", 0);
        this.notificationStates.put("messages", 0);
        this.notificationStates.put("notifications", 0);
    }

    private void SetupWebViewClient() {
        this.webViewFacebook.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest webResourceRequest) {
                String url_css = webResourceRequest.getUrl().toString();
                if (savedPreferences.getBoolean("pref_noBar", true)) {
                    if (webResourceRequest.getUrl().toString().contains(".css")) {
                        String css = getCSSStringFromServer(webResourceRequest.getUrl());
                        //ByteArrayInputStream css = new ByteArrayInputStream(getString(R.string.jT1iNd9vJ_t_css).getBytes());
                        if (css != null) {
                            Pattern pattern = Pattern.compile("\\._52z5\\{([^}]*)\\}");
                            Matcher matcher = pattern.matcher(css);
                            StringBuffer newCSSResponse = new StringBuffer();
                            while (matcher.find()) {
                                matcher.appendReplacement(newCSSResponse, "._52z5{display: none;" + matcher.group(1) + "}");
                            }
                            matcher.appendTail(newCSSResponse);
                            HashMap<String, String> responseHeaders = new HashMap<String, String>();
                            responseHeaders.put("access-control-allow-origin", "*");
                            responseHeaders.put("timing-allow-origin", "*");
                            //WebResourceResponse webResourceResponse = getCssWebResourceResponseFromAsset();//new WebResourceResponse("text/css", "utf-8", css);
                            ByteArrayInputStream cssResponse = new ByteArrayInputStream(newCSSResponse.toString().getBytes());
                            WebResourceResponse webResourceResponse = getUtf8EncodedCssWebResourceResponse(cssResponse);
                            webResourceResponse.setResponseHeaders(responseHeaders);
                            return webResourceResponse;
                        }else
                        {
                            return super.shouldInterceptRequest(webView,webResourceRequest);
                        }
                        //return getCssWebResourceResponseFromAsset();
                    } else {
                        return super.shouldInterceptRequest(webView, webResourceRequest);
                    }

                } else {
                    return super.shouldInterceptRequest(webView, webResourceRequest);
                }
            }
        });

    }

    private String getCSSStringFromServer(Uri url) {
        ByteArrayInputStream byteArrayInputStream = null;
        HttpURLConnection httpURLConnection = null;
        BufferedReader bufferedReader=null;
        if (isInternetAvailable()) {
            try {
                httpURLConnection = (HttpURLConnection) ((URL) new URL(url.toString())).openConnection();
                httpURLConnection.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
                httpURLConnection.connect();
                InputStream inputStream = httpURLConnection.getInputStream();
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = bufferedReader.readLine()) != null) {
                    buffer.append(line + "\n");
                    Log.d("Response: ", "> " + line);   //here u ll get whole response...... :-)

                }
                return buffer.toString();
            } catch (Exception e) {
                //Something went wrong
                e.printStackTrace();
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
                try {
                    if (bufferedReader != null)
                        bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private WebResourceResponse getCssWebResourceResponseFromAsset() {
        try {
            return getUtf8EncodedCssWebResourceResponse(getAssets().open("style/jT1iNd9vJ-t.css"));
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getUtf8EncodedCssWebResourceResponse(InputStream data) {
        return new WebResourceResponse("text/css", "UTF-8", data);
    }

    @SuppressLint("NewApi")
    @Override
    protected void onResume() {
        super.onResume();
        webViewFacebook.onResume();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onPause() {
        webViewFacebook.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.e("Info", "onDestroy()");
        webViewFacebook.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        webViewFacebook.onActivityResult(requestCode, resultCode, intent);
    }

    // app is already running and gets a new intent (used to share link without open another activity)
    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        setIntent(intent);

        // grab an url if opened by clicking a link
        String webViewUrl = getIntent().getDataString();

        /** get a subject and text and check if this is a link trying to be shared */
        String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d("sharedUrl", "onNewIntent() - sharedUrl: " + sharedUrl);
        // if we have a valid URL that was shared by us, open the sharer
        if (sharedUrl != null) {
            if (!sharedUrl.equals("")) {
                // check if the URL being shared is a proper web URL
                if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
                    // if it's not, let's see if it includes an URL in it (prefixed with a message)
                    int startUrlIndex = sharedUrl.indexOf("http:");
                    if (startUrlIndex > 0) {
                        // seems like it's prefixed with a message, let's trim the start and get the URL only
                        sharedUrl = sharedUrl.substring(startUrlIndex);
                    }
                }
                // final step, set the proper Sharer...
                webViewUrl = String.format("https://m.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
                // ... and parse it just in case
                webViewUrl = Uri.parse(webViewUrl).toString();
            }
        }

        if (webViewUrl != null)
            webViewFacebook.loadUrl(FromDesktopToMobileUrl(webViewUrl));


        // recreate activity when something important was just changed
        if (getIntent().getBooleanExtra("settingsChanged", false)) {
            finish(); // close this
            Intent restart = new Intent(MainActivity.this, MainActivity.class);
            startActivity(restart);//reopen this
        }
    }

    //*********************** SETUP ****************************

    private void SetupWebView() {
        webViewFacebook = findViewById(webView);
        webViewFacebook.setListener(this, this);

        webViewFacebook.clearPermittedHostnames();
        webViewFacebook.addPermittedHostname("facebook.com");
        webViewFacebook.addPermittedHostname("fbcdn.net");
        webViewFacebook.addPermittedHostname("fb.com");
        webViewFacebook.addPermittedHostname("fb.me");

/*
        webViewFacebook.addPermittedHostname("m.facebook.com");
        webViewFacebook.addPermittedHostname("h.facebook.com");
        webViewFacebook.addPermittedHostname("touch.facebook.com");
        webViewFacebook.addPermittedHostname("mbasic.facebook.com");
        webViewFacebook.addPermittedHostname("touch.facebook.com");
        webViewFacebook.addPermittedHostname("messenger.com");
*/
        webViewFacebook.requestFocus(View.FOCUS_DOWN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);//remove the keyboard issue

        WebSettings settings = webViewFacebook.getSettings();

        webViewFacebook.setDesktopMode(true);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
        settings.setJavaScriptEnabled(true);

        //set text zoom
        int zoom = Integer.parseInt(savedPreferences.getString("pref_textSize", "100"));
        settings.setTextZoom(zoom);

        //set Geolocation
        settings.setGeolocationEnabled(savedPreferences.getBoolean("pref_allowGeolocation", true));

        // Use WideViewport and Zoom out if there is no viewport defined
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // better image sizing support
        settings.setSupportZoom(true);
        settings.setDisplayZoomControls(false);
        settings.setBuiltInZoomControls(true);

        // set caching
        settings.setAppCachePath(getCacheDir().getAbsolutePath());
        settings.setAppCacheEnabled(true);

        settings.setLoadsImagesAutomatically(!savedPreferences.getBoolean("pref_doNotDownloadImages", false));//to save data

        settings.setDisplayZoomControls(false);
        settings.setAllowUniversalAccessFromFileURLs(true);
    }

    private void SetupOnLongClickListener() {
        // OnLongClickListener for detecting long clicks on links and images
        webViewFacebook.setOnLongClickListener(v -> {

            WebView.HitTestResult result = webViewFacebook.getHitTestResult();
            int type = result.getType();
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    || type == WebView.HitTestResult.IMAGE_TYPE) {
                Message msg = linkHandler.obtainMessage();
                webViewFacebook.requestFocusNodeHref(msg);
            }
            return false;
        });

        webViewFacebook.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_UP:
                    if (!v.hasFocus()) {
                        v.requestFocus();
                    }
                    break;
            }
            return false;
        });
    }

    private void SetupFullScreenVideo() {
        //full screen video
        mTargetView = findViewById(R.id.target_view);
        myWebChromeClient = new WebChromeClient() {
            //this custom WebChromeClient allow to show video on full screen
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                mCustomViewCallback = callback;
                mTargetView.addView(view);
                mCustomView = view;
                swipeRefreshLayout.setVisibility(View.GONE);
                mTargetView.setVisibility(View.VISIBLE);
                mTargetView.bringToFront();
            }

            @Override
            public void onHideCustomView() {
                if (mCustomView == null)
                    return;

                mCustomView.setVisibility(View.GONE);
                mTargetView.removeView(mCustomView);
                mCustomView = null;
                mTargetView.setVisibility(View.GONE);
                mCustomViewCallback.onCustomViewHidden();
                swipeRefreshLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        };
        webViewFacebook.setWebChromeClient(myWebChromeClient);
    }

    private void ShareLinkHandler() {
        /** get a subject and text and check if this is a link trying to be shared */
        String sharedSubject = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        String sharedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d("sharedUrl", "ShareLinkHandler() - sharedUrl: " + sharedUrl);

        // if we have a valid URL that was shared by us, open the sharer
        if (sharedUrl != null) {
            if (!sharedUrl.equals("")) {
                // check if the URL being shared is a proper web URL
                if (!sharedUrl.startsWith("http://") || !sharedUrl.startsWith("https://")) {
                    // if it's not, let's see if it includes an URL in it (prefixed with a message)
                    int startUrlIndex = sharedUrl.indexOf("http:");
                    if (startUrlIndex > 0) {
                        // seems like it's prefixed with a message, let's trim the start and get the URL only
                        sharedUrl = sharedUrl.substring(startUrlIndex);
                    }
                }
                // final step, set the proper Sharer...
                urlSharer = String.format("https://touch.facebook.com/sharer.php?u=%s&t=%s", sharedUrl, sharedSubject);
                // ... and parse it just in case
                urlSharer = Uri.parse(urlSharer).toString();
                isSharer = true;
            }
        }

    }

    private void SetTheme() {
        switch (savedPreferences.getString("pref_theme", "default")) {
            case "DarkTheme": {
                setTheme(R.style.DarkTheme);
                break;
            }
            default: {
                setTheme(R.style.DefaultTheme);
                break;
            }
        }
    }

    private void SetupRefreshLayout() {
        swipeRefreshLayout = findViewById(R.id.swipe_container);
        swipeRefreshLayout.setColorSchemeResources(
                R.color.officialBlueFacebook, R.color.darkBlueSlimFacebookTheme);// set the colors
        //reload the page
        swipeRefreshLayout.setOnRefreshListener(this::RefreshPage);
    }


    //*********************** WEBVIEW FACILITIES ****************************
    private void GoHome() {
        if (savedPreferences.getBoolean("pref_recentNewsFirst", false)) {
            webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_chr");
        } else {
            webViewFacebook.loadUrl(getString(R.string.urlFacebookMobile) + "?sk=h_nor");
        }
    }

    private void GoNotifications() {
        webViewFacebook.loadUrl(getString(R.string.urlFacebookMobileNotifications));

    }

    private void GoMessages() {
        webViewFacebook.loadUrl(getString(R.string.urlFacebookMobileMessages));
    }

    private void GoFriends() {
        webViewFacebook.loadUrl(getString(R.string.urlFacebookMobileFriends));
    }

    private void GoBookmarks() {
        webViewFacebook.loadUrl(getString(R.string.urlFacebookMobileBookmarks));
    }

    private void GoSearch() {
        webViewFacebook.loadUrl(getString(R.string.urlFacebookMobileSearch));
    }

    private void RefreshPage() {
        if (noConnectionError) {
            webViewFacebook.goBack();
            noConnectionError = false;
        } else webViewFacebook.reload();
    }


    //*********************** WEBVIEW EVENTS ****************************
    @Override
    public boolean shouldLoadUrl(String url) {
        Log.d("MainActivity", "shouldLoadUrl: " + url);
        //Check is it's opening a image
        boolean b = Uri.parse(url).getHost() != null && Uri.parse(url).getHost().endsWith("fbcdn.net");

        if (b) {
            //open the activity to show the pic
            startActivity(new Intent(this, PictureActivity.class).putExtra("URL", url));
        }
        //webViewFacebook.loadUrl(url);
        //ApplyCustomCss();

        return !b;
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        swipeRefreshLayout.setRefreshing(true);

    }

    @Override
    public void onPageFinished(String url) {
        setMenuBarNotificationState(this.webViewFacebook, this.menuBar);
        ApplyCustomCss();
        // MyAdvancedWebView myAdvancedWebView = findViewById(webView);
        //myAdvancedWebView.scrollBy(0,88);
        if (savedPreferences.getBoolean("pref_enableMessagesShortcut", false)) {
            webViewFacebook.loadUrl(getString(R.string.fixMessages));
        }

        swipeRefreshLayout.setRefreshing(false);

    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        // refresh on connection error (sometimes there is an error even when there is a network connection)
        if (isInternetAvailable()) {
        }
        //  if (!isInternetAvailable() && !failingUrl.contains("edge-chat") && !failingUrl.contains("akamaihd")
        // && !failingUrl.contains("atdmt") && !noConnectionError)
        else {
            Log.i("onPageError link", failingUrl);
            String summary = "<h1 style='text-align:center; padding-top:15%; font-size:70px;'>" +
                    getString(R.string.titleNoConnection) +
                    "</h1> <h3 style='text-align:center; padding-top:1%; font-style: italic;font-size:50px;'>" +
                    getString(R.string.descriptionNoConnection) +
                    "</h3>  <h5 style='font-size:30px; text-align:center; padding-top:80%; opacity: 0.3;'>" +
                    getString(R.string.awards) +
                    "</h5>";
            webViewFacebook.loadData(summary, "text/html; charset=utf-8", "utf-8");//load a custom html page
            //to allow to return at the last visited page
            noConnectionError = true;
        }
    }

    @Override
    public void onLoadResource(WebView webView, String URL) {
        if (URL.matches("bookmarks")) {

        }


    }

    public boolean isInternetAvailable() {
        NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType,
                                    long contentLength, String contentDisposition, String userAgent) {

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);

    }


    @Override
    public void onExternalPageRequest(String url) {//if the link doesn't contain 'facebook.com', open it using the browser
        if (Uri.parse(url).getHost() != null && Uri.parse(url).getHost().endsWith("slimsocial.leo")) {
            //he clicked on messages
            startActivity(new Intent(this, MessagesActivity.class));
        } else {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {//this prevents the crash
                Log.e("shouldOverrideUrlLoad", "" + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    //*********************** BUTTON ****************************
    // handling the back button
    @Override
    public void onBackPressed() {
        if (mCustomView != null) {
            myWebChromeClient.onHideCustomView();//hide video player
        } else {
            if (webViewFacebook.canGoBack()) {
                WebBackForwardList wbfl = webViewFacebook.copyBackForwardList();
                int backReferenceId = wbfl.getCurrentIndex() - 1;
                if (backReferenceId > -1) {
                    WebHistoryItem webHistoryItem = wbfl.getItemAtIndex(backReferenceId);
                    String url = webHistoryItem.getUrl();
                    if (url.contains(fbMobileNewsfeedUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.home));
                    } else if (url.contains(fbMobileFriendsUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.friends));
                    } else if (url.contains(fbMobileMessageUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.message));
                    } else if (url.contains(fbMobileNotificationsUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.notifications));
                    } else if (url.contains(fbMobileSearchUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.search));
                    } else if (url.contains(fbMobileBookmarksUrl)) {
                        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.bookmarks));
                    }


                }
                webViewFacebook.goBack();
            } else {
                finish();// close app
            }
        }
    }


    //*********************** MENU ****************************
    //add my menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        this.menuBar = menu;
        setMenuItemActive(this.menuBar, this.menuBar.findItem(R.id.home));
        return super.onCreateOptionsMenu(menu);
    }

    /* @Override
     public boolean onPrepareOptionsMenu(Menu menu)
     {
         menu.findItem(R.id.home).getIcon().setAlpha(alphaSelected);
         return super.onPrepareOptionsMenu(menu);

     }*/
    public void setMenuItemActive(Menu menu, MenuItem item) {
        ColorStateList colorStateList = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            colorStateList = item.getIconTintList();
        }
        if (colorStateList != null) {
            colorStateList = colorStateList.withAlpha(alphaNotSelected);
            setMenuItemColorState(menu, R.id.home, colorStateList);
            setMenuItemColorState(menu, R.id.friends, colorStateList);
            setMenuItemColorState(menu, R.id.message, colorStateList);
            setMenuItemColorState(menu, R.id.notifications, colorStateList);
            setMenuItemColorState(menu, R.id.search, colorStateList);
            setMenuItemColorState(menu, R.id.bookmarks, colorStateList);
            colorStateList = colorStateList.withAlpha(alphaSelected);
            setMenuItemColorState(menu, item.getItemId(), colorStateList);
        } else {
            menu.findItem(R.id.home).getIcon().setAlpha(alphaNotSelected);
            menu.findItem(R.id.friends).getIcon().setAlpha(alphaNotSelected);
            menu.findItem(R.id.message).getIcon().setAlpha(alphaNotSelected);
            menu.findItem(R.id.notifications).getIcon().setAlpha(alphaNotSelected);
            menu.findItem(R.id.search).getIcon().setAlpha(alphaNotSelected);
            menu.findItem(R.id.bookmarks).getIcon().setAlpha(alphaNotSelected);
            item.getIcon().setAlpha(alphaSelected);
        }
    }

    public void setMenuItemColorState(Menu menu, int menuId, ColorStateList colorStateList) {
        MenuItem menuItem = menu.findItem(menuId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menuItem.setIconTintList(colorStateList);
        }
    }

    //handling the tap on the menu's items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.top: {//scroll on the top of the page
                webViewFacebook.scrollTo(0, 0);
                break;
            }
            case R.id.openInBrowser: {//open the actual page into using the browser
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webViewFacebook.getUrl())));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "Turn on data please", Toast.LENGTH_SHORT).show();
                }
                break;
            }
            /*case R.id.messages: {//open messages
                startActivity(new Intent(this, MessagesActivity.class));
                break;
            }*/
            case R.id.refresh: {//refresh the page
                RefreshPage();
                break;
            }
            case R.id.home: {//go to the home
                GoHome();
                break;
            }
            case R.id.shareLink: {//share this page
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, MyHandler.cleanUrl(webViewFacebook.getUrl()));
                startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.shareThisLink)));

                break;
            }
            case R.id.share: {//share this app
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, getResources().getString(R.string.downloadThisApp));
                startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.shareThisApp)));

                Toast.makeText(getApplicationContext(), getResources().getString(R.string.thanks),
                        Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.settings: {//open settings
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }

            case R.id.exit: {//open settings
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
                return true;
            }

            case R.id.message: {
                GoMessages();
                break;
            }
            case R.id.notifications: {
                GoNotifications();
                break;
            }
            case R.id.bookmarks: {
                GoBookmarks();
                break;
            }
            case R.id.search: {
                GoSearch();
                break;
            }
            case R.id.friends: {
                GoFriends();
                break;
            }
            default:
                break;
        }
        ContextMenu.ContextMenuInfo contextMenuInfo = ((ContextMenu.ContextMenuInfo) item.getMenuInfo());

        setMenuItemActive(this.menuBar, item);
        return true;
        //return super.onOptionsItemSelected(item);
    }

    public void setMenuBarNotificationState(WebView wv, Menu menu) {
        wv.evaluateJavascript(
                "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String html) {
                        Log.d("HTML", html);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            notificationStates.forEach((key, value) -> {
                                if (html.contains(key)) {
                                    Pattern pattern = Pattern.compile("\\\\u003Cdiv\\s+class=\\\\\"_59te\\s+jewel\\s+_hzb.*?\\s+([A-Za-z]*?)Count\\\\\".*?id=\\\\\"([^_]*)_jewel\\\\\"[^>]*>\\\\u003Ca[^>]*>\\\\u003Cspan[^>]*>[^>]*\\\\u003C/span>\\\\u003Cdiv[^>]*>\\\\u003Cdiv[^>]*>\\\\u003Cspan class=\\\\\"_59tg\\\\\" data-sigil=\\\\\"count\\\\\">(\\d+)\\\\u003C/span>\\\\u003C/div>\\\\u003C/div>\\\\u003C/a>\\\\u003C/div>", Pattern.DOTALL);
                                    //Pattern pattern = Pattern.compile("\\\\u003Cdiv class=\\\"_59te jewel _hzb.*?\\s+([A-Za-z]*)Count\\\".*?id=\\\"([^_]*)_jewel\\\"[^>]*>\\\\u003Ca[^>]*>\\\\u003Cspan[^>]*>[^>]*\\\\u003C/span>\\\\u003Cdiv[^>]*>\\\\u003Cdiv[^>]*>\\\\u003Cspan class=\\\"_59tg\\\" data-sigil=\\\"count\\\">(\\d+)\\\\u003C/span>\\\\u003C/div>\\\\u003C/div>\\\\u003C/a>\\\\u003C/div>");
                                    //Pattern pattern = Pattern.compile("div\\s+class=\\\\\"");

                                    Matcher matcher = pattern.matcher(html);
                                    while (matcher.find()) {
                                        String type = matcher.group(2);
                                        String notificationAvailable = matcher.group(1);
                                        int count = Integer.parseInt(matcher.group(3));
                                        notificationStates.put(type, count);
                                    }
                                }
                            });
                        } else {
                            for (String key : notificationStates.keySet()) {

                                if (html.contains(key)) {
                                    Pattern pattern = Pattern.compile("\\u003Cdiv class=\"_59te jewel _hzb _2cnm\\s(.*)Count\".*id=\"([^_]*)_jewel\"[^>]*>\\u003Ca[^>]*>\\u003Cspan[^>]*>[^>]*\\u003C/span>\\u003Cdiv[^>]*>\\u003Cdiv[^>]*>\\u003Cspan class=\"_59tg\" data-sigil=\"count\">(\\d+)\\u003C/span>\\u003C/div>\\u003C/div>\\u003C/a>\\u003C/div>");
                                    Matcher matcher = pattern.matcher(html);
                                    while (matcher.find()) {
                                        String type = matcher.group(2);
                                        String notificationAvailable = matcher.group(1);
                                        int count = Integer.parseInt(matcher.group(3));
                                        notificationStates.put(type, count);
                                    }
                                }
                            }
                        }
                        /*if (notificationStates.get("feed") > 0)
                        {
                            menu.findItem(R.id.home).setIcon(new BitmapDrawable(getResources(),setNotificationRect((BitmapDrawable)(menu.findItem(R.id.home).getIcon().getCurrent()),notificationStates.get("feed"))));
                        }else if (notificationStates.get("requests") > 0)
                        {
                            menu.findItem(R.id.friends).setIcon(new BitmapDrawable(getResources(),setNotificationRect((BitmapDrawable)(menu.findItem(R.id.friends).getIcon().getCurrent()),notificationStates.get("feed"))));
                        }else if (notificationStates.get("messages") > 0)
                        {
                            menu.findItem(R.id.message).setIcon(new BitmapDrawable(getResources(),setNotificationRect((BitmapDrawable)(menu.findItem(R.id.message).getIcon().getCurrent()),notificationStates.get("feed"))));
                        }else if (notificationStates.get("notifications") > 0)
                        {
                            menu.findItem(R.id.notifications).setIcon(new BitmapDrawable(getResources(),setNotificationRect((BitmapDrawable)(menu.findItem(R.id.notifications).getIcon().getCurrent()),notificationStates.get("feed"))));
                        }
                        */


                    }

                });

    }

    public Bitmap setNotificationRect(BitmapDrawable icon, Integer count) {
        float[] transform =
                {1.0f, 0, 0, 0, 0,
                        0, 1.0f, 0, 0, 0,
                        0, 0, 1.0f, 0, 0,
                        0, 0, 0, 1 - 0f, 0

                };
        ColorFilter colorFilter = new ColorMatrixColorFilter(transform);
        int height = icon.getBitmap().getHeight();
        int width = icon.getBitmap().getWidth();
        RectF rectF = new RectF(width - (width / 4), 0f, width, height - (height / 4));
        Paint fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        fillPaint.setColor(0xff0000);
        Bitmap iconWithNotification = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas combinedImage = new Canvas(iconWithNotification);
        combinedImage.drawBitmap(icon.getBitmap(), new Matrix(), null);
        Paint paint = new Paint();
        paint.setColorFilter(colorFilter);
        combinedImage.drawRoundRect(rectF, 30, 30, fillPaint);
        fillPaint.setColor(0xffffff);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setTextSize(height / 8);
        fillPaint.setTextAlign(Paint.Align.LEFT);
        combinedImage.drawText(count.toString(), width - (width / 4) + (height / 16), height / 16, fillPaint);
        return iconWithNotification;
    }
    //*********************** OTHER ****************************

    String FromDesktopToMobileUrl(String url) {
        if (Uri.parse(url).getHost() != null && Uri.parse(url).getHost().endsWith("facebook.com")) {
            url = url.replace("mbasic.facebook.com", "touch.facebook.com");
            url = url.replace("www.facebook.com", "touch.facebook.com");
        }
        return url;
    }

    private void ApplyCustomCss() {
        String css = "";
        if (savedPreferences.getBoolean("pref_centerTextPosts", false)) {
            css += getString(R.string.centerTextPosts);
        }
        if (savedPreferences.getBoolean("pref_addSpaceBetweenPosts", false)) {
            css += getString(R.string.addSpaceBetweenPosts);
        }
        if (savedPreferences.getBoolean("pref_hideSponsoredPosts", false)) {
            css += getString(R.string.hideAdsAndPeopleYouMayKnow);
        }
        if (savedPreferences.getBoolean("pref_fixedBar", false)) {//without add the barHeight doesn't scroll
            css += (getString(R.string.fixedBar).replace("$s", ""
                    + Dimension.heightForFixedFacebookNavbar(getApplicationContext())));
        }
        /*if (savedPreferences.getBoolean("pref_noBar", true)) {
            css += getString(R.string.noBar);
        }*/
        if (savedPreferences.getBoolean("pref_removeMessengerDownload", true)) {
            css += getString(R.string.removeMessengerDownload);
        }


        switch (savedPreferences.getString("pref_theme", "standard")) {
            case "DarkTheme":
            case "DarkNoBar": {
                css += getString(R.string.blackTheme);
                break;
            }
            default:
                break;
        }

        //apply the customizations
        webViewFacebook.loadUrl(getString(R.string.editCss).replace("$css", css));
    }

    // handle long clicks on links, an awesome way to avoid memory leaks
    private static class MyHandler extends Handler {
        MainActivity activity;
        //thanks to FaceSlim
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            this.activity = activity;
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SharedPreferences savedPreferences = PreferenceManager.getDefaultSharedPreferences(activity); // setup the sharedPreferences
            if (savedPreferences.getBoolean("pref_enableFastShare", true)) {
                MainActivity activity = mActivity.get();
                if (activity != null) {

                    // get url to share
                    String url = (String) msg.getData().get("url");

                    if (url != null) {
                    /* "clean" an url to remove Facebook tracking redirection while sharing
                    and recreate all the special characters */
                        url = decodeUrl(cleanUrl(url));

                        // create share intent for long clicked url
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, url);
                        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.shareThisLink)));
                    }
                }
            }
        }

        // "clean" an url and remove Facebook tracking redirection
        private static String cleanUrl(String url) {
            return url.replace("http://lm.facebook.com/l.php?u=", "")
                    .replace("https://m.facebook.com/l.php?u=", "")
                    .replace("http://0.facebook.com/l.php?u=", "")
                    .replaceAll("&h=.*", "").replaceAll("\\?acontext=.*", "");
        }

        // url decoder, recreate all the special characters
        private static String decodeUrl(String url) {
            return url.replace("%3C", "<").replace("%3E", ">")
                    .replace("%23", "#").replace("%25", "%")
                    .replace("%7B", "{").replace("%7D", "}")
                    .replace("%7C", "|").replace("%5C", "\\")
                    .replace("%5E", "^").replace("%7E", "~")
                    .replace("%5B", "[").replace("%5D", "]")
                    .replace("%60", "`").replace("%3B", ";")
                    .replace("%2F", "/").replace("%3F", "?")
                    .replace("%3A", ":").replace("%40", "@")
                    .replace("%3D", "=").replace("%26", "&")
                    .replace("%24", "$").replace("%2B", "+")
                    .replace("%22", "\"").replace("%2C", ",")
                    .replace("%20", " ");
        }
    }


}
