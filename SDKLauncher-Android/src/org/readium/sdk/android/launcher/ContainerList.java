//  Copyright (c) 2014 Readium Foundation and/or its licensees. All rights reserved.
//  Redistribution and use in source and binary forms, with or without modification, 
//  are permitted provided that the following conditions are met:
//  1. Redistributions of source code must retain the above copyright notice, this 
//  list of conditions and the following disclaimer.
//  2. Redistributions in binary form must reproduce the above copyright notice, 
//  this list of conditions and the following disclaimer in the documentation and/or 
//  other materials provided with the distribution.
//  3. Neither the name of the organization nor the names of its contributors may be 
//  used to endorse or promote products derived from this software without specific 
//  prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
//  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
//  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
//  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
//  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
//  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
//  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
//  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
//  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
//  OF THE POSSIBILITY OF SUCH DAMAGE

package org.readium.sdk.android.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import org.readium.sdk.android.Container;
import org.readium.sdk.android.CredentialHandler;
import org.readium.sdk.android.CredentialRequest;
import org.readium.sdk.android.CredentialRequest.Component;
import org.readium.sdk.android.EPub3;
import org.readium.sdk.android.LCP;
import org.readium.sdk.android.SdkErrorHandler;
import org.readium.sdk.android.launcher.model.BookmarkDatabase;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

/**
 * @author chtian
 * 
 */
@SuppressLint("NewApi") public class ContainerList extends Activity implements SdkErrorHandler {
	
	protected abstract class SdkErrorHandlerMessagesCompleted {
		Intent m_intent = null;
		public SdkErrorHandlerMessagesCompleted(Intent intent) {
			m_intent = intent;
		}
		public void done() {
			if (m_intent != null) {
				once();
				m_intent = null;
			}
		}
		public abstract void once();
	}
	
    private Context context;
    private final String testPath = "epubtest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.container_list);
        context = this;
        BookmarkDatabase.initInstance(getApplicationContext());
        final ListView view = (ListView) findViewById(R.id.containerList);

        final List<String> list = getInnerBooks();

        BookListAdapter bookListAdapter = new BookListAdapter(this, list);
        view.setAdapter(bookListAdapter);

        if (list.isEmpty()) {
            Toast.makeText(
                    context,
                    Environment.getExternalStorageDirectory().getAbsolutePath()
                            + "/" + testPath
                            + "/ is empty, copy epub3 test file first please.",
                    Toast.LENGTH_LONG).show();
        }

        view.setOnItemClickListener(new ListView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
            	
            	String bookName = list.get(arg2);

                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + testPath + "/" + bookName;
            	
                Toast.makeText(context, "Select " + bookName, Toast.LENGTH_SHORT).show();

            	m_SdkErrorHandler_Messages = new Stack<String>();
                
            	//Openbook run on thread
            	OpenBookAsyncTask openBook = new OpenBookAsyncTask();
                openBook.execute(path, bookName);
            }
        });
        
        // Loads the native lib and sets the path to use for cache
        EPub3.setCachePath(getCacheDir().getAbsolutePath());
    }

    private Stack<String> m_SdkErrorHandler_Messages = null;

    // async!
    private void popSdkErrorHandlerMessage(final Context ctx, final SdkErrorHandlerMessagesCompleted callback)
    {
    	if (m_SdkErrorHandler_Messages != null) {
    		
    		if (m_SdkErrorHandler_Messages.size() == 0) {
    			m_SdkErrorHandler_Messages = null;
    			callback.done();
    			return;
    		}
    		
    		String message = m_SdkErrorHandler_Messages.pop(); 
		
			AlertDialog.Builder alertBuilder  = new AlertDialog.Builder(ctx);

			alertBuilder.setTitle("EPUB warning");
			alertBuilder.setMessage(message);

			alertBuilder.setCancelable(false);
			
			alertBuilder.setOnCancelListener(
				new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						m_SdkErrorHandler_Messages = null;
						callback.done();
					}
				}
			);

			alertBuilder.setOnDismissListener(
				new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						popSdkErrorHandlerMessage(ctx, callback);
					}
				}
			);
			
			alertBuilder.setPositiveButton("Ignore",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
			    	}
				);
			alertBuilder.setNegativeButton("Ignore all",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
		    	}
			);
			
			AlertDialog alert = alertBuilder.create();
			alert.setCanceledOnTouchOutside(false);
			
			alert.show(); //async!
		}
    	else {
    		callback.done();
    	}
    }
    
	@Override
	public boolean handleSdkError(String message, boolean isSevereEpubError) {
	        
	    System.out.println("SdkErrorHandler: " + message + " (" + (isSevereEpubError ? "warning" : "info") + ")");
	
	    if (m_SdkErrorHandler_Messages != null && isSevereEpubError) { 
	    	m_SdkErrorHandler_Messages.push(message);
	    }
	    
		// never throws an exception
		return true;
	}
	
    // get books in /sdcard/epubtest path
    private List<String> getInnerBooks() {
        List<String> list = new ArrayList<String>();
        File sdcard = Environment.getExternalStorageDirectory();
        File epubpath = new File(sdcard, "epubtest");
        epubpath.mkdirs();
        File[] files = epubpath.listFiles();
		if (files != null) {
	        for (File f : files) {
	            if (f.isFile()) {
	                String name = f.getName();
	                if (name.length() > 5
	                        && name.substring(name.length() - 5).equals(".epub")) {
	
	                    list.add(name);
	                    Log.i("books", name);
	                }
	            }
	        }
        }
		Collections.sort(list, new Comparator<String>() {

			@Override
			public int compare(String s1, String s2) {
				return s1.compareToIgnoreCase(s2);
			}

		});
        return list;
    }

    public class OpenBookAsyncTask extends AsyncTask<String, Vector<Component>, Container> implements CredentialHandler{
 
    	protected String bookName;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
         
        @Override
        protected Container doInBackground(String...params) {

        	bookName = params[1];
        	EPub3.setSdkErrorHandler(ContainerList.this);
        	LCP.InitializeLCP();
        	LCP.setCredentialHandler(this);
        	Container container = EPub3.openBook(params[0]);
        	EPub3.setSdkErrorHandler(null);
        	LCP.setCredentialHandler(null);
        	return container;
        }        
        @Override
        protected void onPostExecute(Container container) {
        	if(container == null)
        		return;
        	ContainerHolder.getInstance().put(container.getNativePtr(), container);

        	Intent intent = new Intent(getApplicationContext(), BookDataActivity.class);
        	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        	intent.putExtra(Constants.BOOK_NAME, bookName);
        	intent.putExtra(Constants.CONTAINER_ID, container.getNativePtr());
        	
        	SdkErrorHandlerMessagesCompleted callback = new SdkErrorHandlerMessagesCompleted(intent) {
				@Override
				public void once() {
	                startActivity(m_intent);
				}
          };

          popSdkErrorHandlerMessage(context, callback);
        }
         
        @Override
        protected void onCancelled() {
        	super.onCancelled();
        }

        public AlertDialog alertD;
        
        @Override
        protected void onProgressUpdate(Vector<Component>... param) {
        	Vector<Component> components = param[0];

	    	Log.i("OpenBookAsyncTask", "onProgressUpdate : " + components.size());
        	LinearLayout layout = new LinearLayout(context);
        	layout.setOrientation(LinearLayout.VERTICAL);
        	
        	AlertDialog.Builder alertBuilder  = new AlertDialog.Builder(context);
			alertBuilder.setTitle(components.get(0).m_title);
			alertBuilder.setMessage(components.get(1).m_title);
			for(int i=2; i<components.size(); i++)
			{
				Component component = components.get(i);
				CredentialRequest.Type type = component.m_type;
				if(type == CredentialRequest.Type.Message){
					
				}else if(type == CredentialRequest.Type.TextInput){
					EditText input = new EditText(context);
					layout.addView(input);
				}else if(type == CredentialRequest.Type.MaskedInput){
					EditText input = new EditText(context);
					input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_CLASS_TEXT);
					input.setTag(component);
					layout.addView(input);
					
					Button button = new Button(context);
					button.setText("ok");
					button.setTag(input);
					button.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							alertD.dismiss();
							EditText input = (EditText)view.getTag();
							Component component = (Component)input.getTag();
							LCP.CredentialResult(component.m_index, input.getText().toString());
						}
			    	});
					layout.addView(button);
				}else if(type == CredentialRequest.Type.Button){
	                if(components.get(i).m_title.equals("Forgot password"))
	                	continue;

					Button button = new Button(context);
					button.setText(components.get(i).m_title);
					button.setTag(component);
					button.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View view) {
							alertD.dismiss();
							Component component = (Component)view.getTag();
							LCP.CredentialResult(component.m_index, "");
						}
			    	});
					layout.addView(button);
				}
			}
			alertBuilder.setView(layout);
			alertBuilder.setCancelable(false);
			alertD = alertBuilder.create();
			alertD.show();
        }
        
		@Override
		public boolean handleCredential(Vector<Component> components) {
	    	Log.i("OpenBookAsyncTask", "on handleCredential");
			publishProgress(components);
			return false;
		}
    }
}
