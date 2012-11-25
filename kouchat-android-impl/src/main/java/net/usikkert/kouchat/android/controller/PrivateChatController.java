
/***************************************************************************
 *   Copyright 2006-2012 by Christian Ihle                                 *
 *   kontakt@usikkert.net                                                  *
 *                                                                         *
 *   This file is part of KouChat.                                         *
 *                                                                         *
 *   KouChat is free software; you can redistribute it and/or modify       *
 *   it under the terms of the GNU Lesser General Public License as        *
 *   published by the Free Software Foundation, either version 3 of        *
 *   the License, or (at your option) any later version.                   *
 *                                                                         *
 *   KouChat is distributed in the hope that it will be useful,            *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU      *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with KouChat.                                           *
 *   If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

package net.usikkert.kouchat.android.controller;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.android.AndroidPrivateChatWindow;
import net.usikkert.kouchat.android.AndroidUserInterface;
import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.service.ChatService;
import net.usikkert.kouchat.android.service.ChatServiceBinder;
import net.usikkert.kouchat.misc.User;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Controller for private chat with another user.
 *
 * @author Christian Ihle
 */
public class PrivateChatController extends Activity {

    private AndroidUserInterface androidUserInterface;
    private ServiceConnection serviceConnection;
    private User user;
    private TextView privateChatView;
    private EditText privateChatInput;
    private AndroidPrivateChatWindow privateChatWindow;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.private_chat);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.kou_icon_16x16);

        privateChatInput = (EditText) findViewById(R.id.privateChatInput);
        privateChatView = (TextView) findViewById(R.id.privateChatView);

        final Intent chatServiceIntent = createChatServiceIntent();
        serviceConnection = createServiceConnection();
        bindService(chatServiceIntent, serviceConnection, BIND_NOT_FOREGROUND);

        registerPrivateChatInputListener();
        makePrivateChatViewScrollable();
        makeLinksClickable();
    }

    @Override
    protected void onDestroy() {
        privateChatWindow.unregisterPrivateChatController();
        unbindService(serviceConnection);
        super.onDestroy();
    }

    private Intent createChatServiceIntent() {
        return new Intent(this, ChatService.class);
    }

    private ServiceConnection createServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
                final ChatServiceBinder binder = (ChatServiceBinder) iBinder;
                androidUserInterface = binder.getAndroidUserInterface();

                setupPrivateChatWithUser();
            }

            @Override
            public void onServiceDisconnected(final ComponentName componentName) {

            }
        };
    }

    private void registerPrivateChatInputListener() {
        privateChatInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    sendPrivateMessage(privateChatInput.getText().toString());
                    privateChatInput.setText("");

                    return true;
                }

                return false;
            }
        });
    }

    private void makePrivateChatViewScrollable() {
        privateChatView.setMovementMethod(new ScrollingMovementMethod());
    }

    private void makeLinksClickable() {
        // This needs to be done after making the text view scrollable, or else the links wont be clickable.
        // This also makes the view scrollable, but setting both movement methods seems to make the scrolling
        // behave better.
        privateChatView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void sendPrivateMessage(final String privateMessage) {
        if (privateMessage != null && privateMessage.trim().length() > 0) {
            androidUserInterface.sendPrivateMessage(privateMessage, user);
        }
    }

    private void setupPrivateChatWithUser() {
        setUser();
        setTitle();
        setPrivateChatWindow();
    }

    private void setTitle() {
        setTitle(user.getNick() + " - " + Constants.APP_NAME);
    }

    private void setUser() {
        final Intent intent = getIntent();

        final int userCode = intent.getIntExtra("userCode", -1);
        user = androidUserInterface.getUser(userCode);
    }

    private void setPrivateChatWindow() {
        androidUserInterface.createPrivChat(user);

        privateChatWindow = (AndroidPrivateChatWindow) user.getPrivchat();
        privateChatWindow.registerPrivateChatController(this);
    }

    public void updatePrivateChat(final SpannableStringBuilder savedChat) {
        privateChatView.setText(savedChat);
        Linkify.addLinks(privateChatView, Linkify.WEB_URLS);

        // Run this after 1 second, because right after a rotate the layout is null and it's not possible to scroll yet
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollPrivateChatViewToBottom();
            }
        }, 1000);
    }

    public void appendToPrivateChat(final String message, final int color) {
        runOnUiThread(new Runnable() {
            public void run() {
                final SpannableStringBuilder builder = new SpannableStringBuilder(message + "\n");
                builder.setSpan(new ForegroundColorSpan(color), 0, message.length(), 0);
                Linkify.addLinks(builder, Linkify.WEB_URLS);
                privateChatView.append(builder);
                scrollPrivateChatViewToBottom();
            }
        });
    }

    private void scrollPrivateChatViewToBottom() {
        final Layout layout = privateChatView.getLayout();

        // Happens sometimes when activity is hidden
        if (layout == null) {
            return;

        }

        final int scrollAmount = layout.getLineTop(privateChatView.getLineCount()) - privateChatView.getHeight();

        // if there is no need to scroll, scrollAmount will be <=0
        if (scrollAmount > 0) {
            privateChatView.scrollTo(0, scrollAmount);
        }

        else {
            privateChatView.scrollTo(0, 0);
        }
    }
}
