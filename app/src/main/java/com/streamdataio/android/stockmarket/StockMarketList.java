package com.streamdataio.android.stockmarket;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import tylerjroach.com.eventsource_android.EventSource;
import tylerjroach.com.eventsource_android.EventSourceHandler;
import tylerjroach.com.eventsource_android.MessageEvent;


/**
 * This Activity displays a list with changing values using Streamdata.io proxy
 *
 * @author Streamdata.io
 */
public class StockMarketList extends StockMarketActivity {

    private JsonNode data;
    private ListView listView;
    private MyListAdapter listAdapter;
    private EventSource eventSource;
    private final ObjectMapper mapper = new ObjectMapper();

    private String apiURL = "https://streamdata.motwin.net/http://demo-streamdataio.rhcloud.com/stockmarket/prices";
    private String proxyToken = "MDM3MzM3OTQtMWRmZC00MTg1LWI2YzEtMGFmYWE1MjlhMmQ3";

    /**
     * Android application creation callback.
     *
     * @param savedInstanceState contains environment variables & values
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("onCreate() callback");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Set the Activity layout
        setContentView(R.layout.main);

        try {
            data = mapper.readTree("[]");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Configure the list view
        listView = (ListView) findViewById(R.id.listView);
        listAdapter = new MyListAdapter(this, (ArrayNode) data);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(listAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        System.out.println("onStart() callback");
    }

    @Override
    protected void onStop() {
        super.onStop();
        System.out.println("onStop() callback");
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("onResume() callback ");

        // Connection to EventSource
        this.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("onPause() callback");

        // Disconnection from EventSource
        this.disconnect();
    }

    /**
     * Create the EventSource object & start listening SSE incoming messages
     */
    private void connect() {

        // Add the Streamdata.io authentication token
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Sd-Token", this.proxyToken);

        // Create the EventSource with API URL & Streamdata.io authentication token
        try {
            this.eventSource = new EventSource(new URI(this.apiURL), new SSEHandler(), headers);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // Start data receiving
        this.eventSource.connect();
    }

    /**
     * Closes the event source connection and dereference the EventSource object
     */
    private void disconnect() {

        // Disconnect the eventSource Handler
        if (this.eventSource.isConnected())
        this.eventSource.close();

        // Dereferencing variable
        this.eventSource = null;
    }


    /* ********************************** Class SSEHandler ********************************** */
    private class SSEHandler implements EventSourceHandler {

        public SSEHandler() {
        }

        /**
         * SSE handler for connection starting
         */
        @Override
        public void onConnect() {
            System.out.println("SSE Connected");
        }

        /**
         * SSE incoming message handler
         * @param event type of message
         * @param message message JSON content
         * @throws IOException if JSON syntax is not valid
         */
        @Override
        public void onMessage(String event, MessageEvent message) throws IOException {


            if ("data".equals(event)) {
                // SSE message is a snapshot
                System.out.println("EventSource onData()");
                data = mapper.readTree(message.data);

                // Refresh UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.getData().removeAll();
                        listAdapter.getData().addAll((ArrayNode) data);
                        listAdapter.notifyDataSetChanged();
                    }
                });

            } else if ("patch".equals(event)) {
                // SSE message is a patch
                System.out.println("EventSource onPatch()");
                try {
                    JsonNode patchNode = mapper.readTree(message.data);
                    JsonPatch patch = JsonPatch.fromJson(patchNode);
                    data = patch.apply(data);

                    // Refresh UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listAdapter.getData().removeAll();
                            listAdapter.getData().addAll((ArrayNode) data);
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (JsonPatchException e) {
                    e.printStackTrace();
                }
            } else {
                throw new RuntimeException("Wrong SSE message!");
            }
        }

        /**
         * SSE error Handler
         */
        @Override
        public void onError(Throwable t) {
            System.out.println("EventSource onError() !");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
        }

        /**
         * SSE Handler for connection interruption
         */
        @Override
        public void onClosed(boolean willReconnect) {
            System.out.println("SSE Closed - reconnect? " + willReconnect);
        }
    }

/* ********************************** Class MyListAdapter ********************************** */

    public class MyListAdapter extends BaseAdapter {

        /**
         * this is our own collection of data, can be anything we want it to be
         * as long as we get the abstract methods implemented using this data
         * and work on this data (see getter) you should be fine
         */
        private final ArrayNode mData;

        /**
         * some context can be useful for getting colors and other resources for
         * layout
         */
        private final Context mContext;

        /**
         * our ctor for this adapter, we'll accept all the things we need here
         *
         * @param mData
         */
        public MyListAdapter(final Context context, final ArrayNode mData) {
            this.mData = mData;
            this.mContext = context;
        }

        public ArrayNode getData() {
            return mData;
        }

        @Override
        public int getCount() {
            return mData != null ? mData.size() : 0;
        }

        @Override
        public Object getItem(int i) {
            return mData != null ? mData.get(i) : null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            // This is where we'll be creating our view, anything that needs to  update according
            // to your model object will need a view to visualize the state of that property
            ViewWrapper viewWrapper;
            // The viewholder pattern for performance
            if (convertView == null) {

                // inflate the layout, see how we can use this context reference?
                LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
                convertView = inflater.inflate(R.layout.cell, parent, false);

                // Store view elements in the Tag to improve list performance
                viewWrapper = new ViewWrapper();
                viewWrapper.titleTextView = (TextView) convertView.findViewById(R.id.title);
                viewWrapper.priceLayout = (TextView) convertView.findViewById(R.id.price);
                convertView.setTag(viewWrapper);

            } else {
                // we've just avoided calling findViewById() on resource every time just use the viewHolder instead
                viewWrapper = (ViewWrapper) convertView.getTag();
            }

            // object item based on the position
            JsonNode obj = mData.get(position);

            // assign values if the object is not null
            if (mData != null) {
                // get the TextView from the ViewHolder and then set the text
                // (item name) and other values
                viewWrapper.titleTextView.setText(obj.get("title").asText());
                viewWrapper.priceLayout.setText(obj.get("price").asText());
            }
            return convertView;
        }

    }


  /* ********************************** Class ViewWrapper ********************************** */

    private static class ViewWrapper {
        TextView titleTextView;
        TextView priceLayout;
    }
}