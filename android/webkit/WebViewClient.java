/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.ViewRootImpl;

import java.security.Principal;

/**
 * 比如处理SSL错误,比如用户自动登录,比如处理一些重定向的问题 等等,针对这些情况,WebViewClient提供了不少方法给应用去实现处理.
 * 但作为一般的使用,我们并不需要那么多.常见的shouldOverrideUrlLoading(),onPageStarted(),onPageFinished(),onReceivedError(),
 * 这些方法已经满足绝大多数的场景.当然如果要实现的是手机浏览器这样的专业水平应用,当然需要熟知所有的方法,而且还要对webkit内核和其他类
 * 进一步的了解.
 *
 * @translator Alex Tam
 */
public class WebViewClient {

    /**
     * 拦截WebView内点击URL跳转的事件.返回true,表示"自己"已经处理该事件,不再传给系统处理. 返回false,表示调用系统浏览器处理.
     *
     * Give the host application a chance to take over the control when a new
     * url is about to be loaded in the current WebView. If WebViewClient is not
     * provided, by default WebView will ask Activity Manager to choose the
     * proper handler for the url. If WebViewClient is provided, return true
     * means the host application handles the url, while return false means the
     * current WebView handles the url.
     *
     * @param view The WebView that is initiating the callback.
     * @param url The url to be loaded.
     * @return True if the host application wants to leave the current WebView
     *         and handle the url itself, otherwise return false.
     */
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return false;
    }

    /**
     * 该方法在页面开始加载时被调用.这个方法在WebView的主要框架首次加载页面的时候被调用一次.
     * 这也意味着当页面内的内容发生改变时,onPageStarted()方法将不被调用.比如在页面内点击了一个连接.
     *
     * Notify the host application that a page has started loading. This method
     * is called once for each main frame load so a page with iframes or
     * framesets will call onPageStarted one time for the main frame. This also
     * means that onPageStarted will not be called when the contents of an
     * embedded frame changes, i.e. clicking a link whose target is an iframe.
     *
     * @param view The WebView that is initiating the callback.
     * @param url The url to be loaded.
     * @param favicon The favicon for this page if it already exists in the
     *            database.
     */
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
    }

    /**
     * 该方法在页面被完成加载时被调用.并且该方法只能被主框架调用.当这个方法被回调时,页面正在加载的图片可能还没更新.
     * 要获得关于新图片的通知,可以使用{@link WebView.PictureListener#onNewPicture}.
     *
     * Notify the host application that a page has finished loading. This method
     * is called only for main frame. When onPageFinished() is called, the
     * rendering picture may not be updated yet. To get the notification for the
     * new Picture, use {@link WebView.PictureListener#onNewPicture}.
     *
     * @param view The WebView that is initiating the callback.
     * @param url The url of the page.
     */
    public void onPageFinished(WebView view, String url) {
    }

    /**
     * 该方法用于通知应用,WebView将加载url指定的资源.
     *
     * Notify the host application that the WebView will load the resource
     * specified by the given url.
     *
     * @param view The WebView that is initiating the callback.
     * @param url The url of the resource the WebView will load.
     */
    public void onLoadResource(WebView view, String url) {
    }

    /**
     * 通知应用来自加载资源的请求,应用会返回数据.如果返回的值为null,WebView将继续正常加载资源.否则返回的响应结果和数据会被应用.
     * 请注意:这个方法是被网络线程回调的,所以当访问比较隐私的数据时,客户端应该谨慎使用.
     *
     * 参数 url : 资源的请求url
     *
     * Notify the host application of a resource request and allow the
     * application to return the data.  If the return value is null, the WebView
     * will continue to load the resource as usual.  Otherwise, the return
     * response and data will be used.  NOTE: This method is called by the
     * network thread so clients should exercise caution when accessing private
     * data.
     *
     * @param view The {@link android.webkit.WebView} that is requesting the
     *             resource.
     * @param url The raw url of the resource.
     * @return A {@link android.webkit.WebResourceResponse} containing the
     *         response information or null if the WebView should load the
     *         resource itself.
     */
    public WebResourceResponse shouldInterceptRequest(WebView view,
                                                      String url) {
        return null;
    }

    /**
     * 通知应用,存在过多的HTTP重定向请求.如果应用继续尝试加载资源.那么默认的处理方式是发送取消的指令信息.
     * (@deprecated 这个方法已经被弃用了.)
     *
     * Notify the host application that there have been an excessive number of
     * HTTP redirects. As the host application if it would like to continue
     * trying to load the resource. The default behavior is to send the cancel
     * message.
     *
     * @param view The WebView that is initiating the callback.
     * @param cancelMsg The message to send if the host wants to cancel
     * @param continueMsg The message to send if the host wants to continue
     * @deprecated This method is no longer called. When the WebView encounters
     *             a redirect loop, it will cancel the load.
     */
    @Deprecated
    public void onTooManyRedirects(WebView view, Message cancelMsg,
                                   Message continueMsg) {
        cancelMsg.sendToTarget();
    }

    // These ints must match up to the hidden values in EventHandler.
    /** Generic error */
    public static final int ERROR_UNKNOWN = -1;
    /** Server or proxy hostname lookup failed */
    public static final int ERROR_HOST_LOOKUP = -2;
    /** Unsupported authentication scheme (not basic or digest) */
    public static final int ERROR_UNSUPPORTED_AUTH_SCHEME = -3;
    /** User authentication failed on server */
    public static final int ERROR_AUTHENTICATION = -4;
    /** User authentication failed on proxy */
    public static final int ERROR_PROXY_AUTHENTICATION = -5;
    /** Failed to connect to the server */
    public static final int ERROR_CONNECT = -6;
    /** Failed to read or write to the server */
    public static final int ERROR_IO = -7;
    /** Connection timed out */
    public static final int ERROR_TIMEOUT = -8;
    /** Too many redirects */
    public static final int ERROR_REDIRECT_LOOP = -9;
    /** Unsupported URI scheme */
    public static final int ERROR_UNSUPPORTED_SCHEME = -10;
    /** Failed to perform SSL handshake */
    public static final int ERROR_FAILED_SSL_HANDSHAKE = -11;
    /** Malformed URL */
    public static final int ERROR_BAD_URL = -12;
    /** Generic file error */
    public static final int ERROR_FILE = -13;
    /** File not found */
    public static final int ERROR_FILE_NOT_FOUND = -14;
    /** Too many requests during this load */
    public static final int ERROR_TOO_MANY_REQUESTS = -15;

    /**
     * (当加载页面时遇到错误时)该方法会被回调向应用报告.这些错误是不可恢复的(比如一些资源无法访问或被加载).
     * 错误码(errorCode)的参数将以其中一个 "ERROR_* + 常量" 的格式出现. 
     *
     * Report an error to the host application. These errors are unrecoverable
     * (i.e. the main resource is unavailable). The errorCode parameter
     * corresponds to one of the ERROR_* constants.
     * @param view The WebView that is initiating the callback.
     * @param errorCode The error code corresponding to an ERROR_* value.
     * @param description A String describing the error.
     * @param failingUrl The url that failed to load.
     */
    public void onReceivedError(WebView view, int errorCode,
                                String description, String failingUrl) {
    }

    /**
     * 当浏览器以POST打开请求的页面,如果浏览器应该再次发送数据,该方法被回调.默认的处理方式是不会再次发送这些数据.
     *
     * As the host application if the browser should resend data as the
     * requested page was a result of a POST. The default is to not resend the
     * data.
     *
     * @param view The WebView that is initiating the callback.
     * @param dontResend The message to send if the browser should not resend
     * @param resend The message to send if the browser should resend data
     */
    public void onFormResubmission(WebView view, Message dontResend,
                                   Message resend) {
        dontResend.sendToTarget();
    }

    /**
     * 通知应用更新它的链接库.
     * 参数 isReload: 如果这个URL被再次加载,则传入true.
     *
     * Notify the host application to update its visited links database.
     *
     * @param view The WebView that is initiating the callback.
     * @param url The url being visited.
     * @param isReload True if this url is being reloaded.
     */
    public void doUpdateVisitedHistory(WebView view, String url,
                                       boolean isReload) {
    }

    /**
     * 该方法当加载资源遇到SSL错误时,通知应用这个错误.应用必须调用handler.cancel()或者handler.proceed()中的其一(取消或继续)方法去处理.
     * 请注意,处理的结果可能是,这个SSL错误在将来的请求响应中继续存在.
     * 应用默认的处理方式是调用cancel()方法,取消加载.
     *
     * Notify the host application that an SSL error occurred while loading a
     * resource. The host application must call either handler.cancel() or
     * handler.proceed(). Note that the decision may be retained for use in
     * response to future SSL errors. The default behavior is to cancel the
     * load.
     *
     * @param view The WebView that is initiating the callback.
     * @param handler An SslErrorHandler object that will handle the user's
     *            response.
     * @param error The SSL error object.
     */
    public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                   SslError error) {
        handler.cancel();
    }

    /**
     * 当加载资源遇到SSL错误时,通知应用这个错误.但无论之前的onReceivedSslError()方法中如何去处理该SSL错误的请求响应,
     * WebView都不会继续这个请求.
     * 该方法作为一种最后处理SSL错误的响应手段,在onReceivedSslError()方法后面跟着被回调.
     *
     * Notify the host application that an SSL error occurred while loading a
     * resource, but the WebView but chose to proceed anyway based on a
     * decision retained from a previous response to onReceivedSslError().
     * @hide
     */
    public void onProceededAfterSslError(WebView view, SslError error) {
    }

    /**
     * 该方法通知应用去处理一个SSL客户端的证书请求(并想用户展示这个请求,询问是否继续).(笔者言,这有点类似我们用电脑浏览器加载某个银行网站,
     * 然后页面弹出一个框,说什么安全证书过期,是否继续访问当前页面.是一种网站安全策略的实现.在手机上,只需要负责调用cancel()或者proceed()
     * 去处理这种请求就好,不必考虑过多的细节.) 应用必须调用cancel()或者proceed()中的其一方法去处理.默认的处理方式是调用cancel()方法,
     * 返回无证书的状态.
     *
     * Notify the host application to handle a SSL client certificate
     * request (display the request to the user and ask whether to
     * proceed with a client certificate or not). The host application
     * has to call either handler.cancel() or handler.proceed() as the
     * connection is suspended and waiting for the response. The
     * default behavior is to cancel, returning no client certificate.
     *
     * @param view The WebView that is initiating the callback.
     * @param handler An ClientCertRequestHandler object that will
     *            handle the user's response.
     * @param host_and_port The host and port of the requesting server.
     *
     * @hide
     */
    public void onReceivedClientCertRequest(WebView view,
                                            ClientCertRequestHandler handler, String host_and_port) {
        handler.cancel();
    }

    /**
     * 该方法通知应用去处理身份认证请求.默认的处理方式是取消请求.
     *
     * Notify the host application to handle an authentication request. The
     * default behavior is to cancel the request.
     *
     * @param view The WebView that is initiating the callback.
     * @param handler The HttpAuthHandler that will handle the user's response.
     * @param host The host requiring authentication.
     * @param realm A description to help store user credentials for future
     *            visits.
     */
    public void onReceivedHttpAuthRequest(WebView view,
                                          HttpAuthHandler handler, String host, String realm) {
        handler.cancel();
    }

    /**
     * 该方法提供应用一个机会去处理按键的同步事件,比如菜单快捷键的事件需要用这个方法去过滤.如果返回true,WebView将不处理按键事件.
     * 如果返回false,WebView将一直处理按键事件,所以父类view的super方法不会"看到"该按键事件.默认的返回值是false.
     *
     * Give the host application a chance to handle the key event synchronously.
     * e.g. menu shortcut key events need to be filtered this way. If return
     * true, WebView will not handle the key event. If return false, WebView
     * will always handle the key event, so none of the super in the view chain
     * will see the key event. The default behavior returns false.
     *
     * @param view The WebView that is initiating the callback.
     * @param event The key event.
     * @return True if the host application wants to handle the key event
     *         itself, otherwise return false
     */
    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
        return false;
    }

    /**
     * 当一个按键没有被WebView处理时,该方法将该事件通知应用.除了系统按键之外,WebView会一直消费正常的按键事件,或者shouldOverrideKeyEvent()
     * 方法返回true,WebView也会消费这个按键事件.按键事件被分发时,这个方法被异步调用.它提供一个"机会"给应用去处理"还没处理"的按键事件.
     *
     * Notify the host application that a key was not handled by the WebView.
     * Except system keys, WebView always consumes the keys in the normal flow
     * or if shouldOverrideKeyEvent returns true. This is called asynchronously
     * from where the key is dispatched. It gives the host application an chance
     * to handle the unhandled key events.
     *
     * @param view The WebView that is initiating the callback.
     * @param event The key event.
     */
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
    }

    /**
     * 该方法通知应用,WebView的大小(scale)发生变化了.
     *
     * Notify the host application that the scale applied to the WebView has
     * changed.
     *
     * @param view he WebView that is initiating the callback.
     * @param oldScale The old scale factor
     * @param newScale The new scale factor
     */
    public void onScaleChanged(WebView view, float oldScale, float newScale) {
    }

    /**
     * 该方法通知应用,一个用户自动登录的请求已经被处理.
     *
     * Notify the host application that a request to automatically log in the
     * user has been processed.
     * @param view The WebView requesting the login.
     * @param realm The account realm used to look up accounts. 用于查找账户
     * @param account An optional account. If not null, the account should be
     *                checked against accounts on the device. If it is a valid
     *                account, it should be used to log in the user.
     * @param args Authenticator specific arguments used to log in the user.
     */
    public void onReceivedLoginRequest(WebView view, String realm,
                                       String account, String args) {
    }
}
