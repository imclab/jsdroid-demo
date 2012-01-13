package org.rhindroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import com.google.dexmaker.stock.ProxyBuilder;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.Wrapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class Main extends Activity
{
    ContextFactory contextFactory;
    ScriptableObject scope;
    Handler handler = new Handler();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        init();
    }
    
    private void init() {
        final ProgressDialog loadingDialog = ProgressDialog.show(
                this, null, "Loading...", true, false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    initScriptEngine();
                    final View view = loadScriptedView("js/mainview.js");
                    handler.post(new Runnable() {
                        public void run() {
                            setContentView(view);
                        }
                    });
                } catch (Exception iox) {
                    throw new RuntimeException(iox);
                } finally {
                    loadingDialog.dismiss();
                }
            }
        }).start();
    }
    
    private void initScriptEngine() {
        contextFactory = new ContextFactory();
        scope = (ScriptableObject) contextFactory.call(new ContextAction() {
            public Object run(org.mozilla.javascript.Context cx) {
                return cx.initStandardObjects();   
            }
        });
    }
    
    protected View loadScriptedView(final String source) throws IOException {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(final Object proxy, final Method method,
                                 final Object[] args) throws Throwable {
                Object result;
                final Object fn = ScriptableObject.getProperty(scope, method.getName());
                if (fn instanceof Function) {
                    result = contextFactory.call(new ContextAction() {
                        public Object run(org.mozilla.javascript.Context cx) {
                            WrapFactory wrapFactory = cx.getWrapFactory();
                            for (int i = 0; i < args.length; i++) {
                                args[i] = wrapFactory.wrap(cx, scope, args[i], null);
                            }
                            Object result = ((Function) fn).call(cx, scope, scope, args);
                            if (result == Undefined.instance) {
                                result = null;
                            } else if (result instanceof Wrapper) {
                                result = ((Wrapper)result).unwrap();
                            }
                            return result;
                        }
                    });
                } else {
                    result = ProxyBuilder.callSuper(proxy, method, args);
                }
                return result;
            }
        };
        final View view =  ProxyBuilder.forClass(View.class)
                .dexCache(getApplicationContext().getDir("dx", Context.MODE_PRIVATE))
                .constructorArgTypes(Context.class)
                .constructorArgValues(this)
                .handler(handler)
                .build();

        final Reader reader = new InputStreamReader(getAssets().open(source));
        contextFactory.call(new ContextAction() {
            public Object run(org.mozilla.javascript.Context cx) {
                cx.setOptimizationLevel(-1);
                cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_1_8);
                WrapFactory wrapFactory = cx.getWrapFactory();
                Object wrapped = wrapFactory.wrap(cx, scope, view, null);
                scope.defineProperty ("view", wrapped, ScriptableObject.READONLY);
                try {
                    cx.evaluateReader(scope, reader, source, 0, null);
                } catch (IOException iox) {
                    throw new RuntimeException(iox);
                }
                return null;
            }
        });
        return view;
    }
}
