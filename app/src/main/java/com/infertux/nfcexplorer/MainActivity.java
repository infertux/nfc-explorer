package com.infertux.nfcexplorer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    static private ArrayList<TagWrapper> tags = new ArrayList<TagWrapper>();
    static private int currentTagIndex = -1;

    private NfcAdapter adapter = null;
    private PendingIntent pendingIntent = null;

    private TextView currentTagView;
    private ExpandableListView expandableListView;

    private float touchDownX, touchUpX;

    @Override
    public void onCreate(final Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.activity_main);

        currentTagView = (TextView) findViewById(R.id.currentTagView);
        currentTagView.setText("Loading...");

        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);

        adapter = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!adapter.isEnabled()) {
            Utils.showNfcSettingsDialog(this);
            return;
        }

        if (pendingIntent == null) {
            pendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

            currentTagView.setText("Scan a tag");
        }

        showTag();

        adapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        adapter.disableForegroundDispatch(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.d("onNewIntent", "Discovered tag with intent " + intent);

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        String tagId = Utils.bytesToHex(tag.getId());
        TagWrapper tagWrapper = new TagWrapper(tagId);

        ArrayList<String> misc = new ArrayList<String>();
        misc.add("scanned at: " + Utils.now());
        misc.add("tag data: " + intent.getDataString());
        tagWrapper.techList.put("misc", misc);

        for (String tech : tag.getTechList()) {
            tech = tech.replace("android.nfc.tech.", "");
            List<String> info = getTagInfo(tag, tech);
            tagWrapper.techList.put(tech, info);
        }

        if (tags.size() == 1) {
            Toast.makeText(this, "Swipe right to see previous tags", Toast.LENGTH_LONG).show();
        }

        tags.add(tagWrapper);
        currentTagIndex = tags.size() - 1;
        showTag();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        final float swipeThreshold = 30;

        switch(event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                break;

            case MotionEvent.ACTION_UP:
                touchUpX = event.getX();
                final float deltaX = touchUpX - touchDownX;

                if (deltaX > swipeThreshold) {
                    showPreviousTag();
                } else if (deltaX < -swipeThreshold) {
                    showNextTag();
                }

                break;
        }

        return super.onTouchEvent(event);
    }

    private void showPreviousTag() {
        if (--currentTagIndex < 0) currentTagIndex = tags.size() - 1;

        showTag();
    }

    private void showNextTag() {
        if (++currentTagIndex >= tags.size()) currentTagIndex = 0;

        showTag();
    }

    private void showTag() {
        if (tags.size() == 0) return;

        final TagWrapper tagWrapper = tags.get(currentTagIndex);
        final TagTechList techList = tagWrapper.techList;
        final ArrayList<String> expandableListTitle = new ArrayList<String>(techList.keySet());

        expandableListView.setAdapter(
                new CustomExpandableListAdapter(this, expandableListTitle, techList));

        final int count = expandableListView.getCount();
        for (int i = 0; i < count; i++) expandableListView.expandGroup(i);

        currentTagView.setText(tagWrapper.getId() + "\n" + (currentTagIndex+1) + "/" + tags.size());
    }

    private final List<String> getTagInfo(final Tag tag, final String tech) {
        List<String> info = new ArrayList<String>();

        switch (tech) {
            case "NfcA":
                info.add("aka ISO 14443-3A");

                NfcA nfcATag = NfcA.get(tag);
                info.add("atqa: " + Utils.bytesToHexAndString(nfcATag.getAtqa()));
                info.add("sak: " + nfcATag.getSak());
                info.add("maxTransceiveLength: " + nfcATag.getMaxTransceiveLength());
                break;

            case "NfcF":
                info.add("aka JIS 6319-4");

                NfcF nfcFTag = NfcF.get(tag);
                info.add("manufacturer: " + Utils.bytesToHex(nfcFTag.getManufacturer()));
                info.add("systemCode: " + Utils.bytesToHex(nfcFTag.getSystemCode()));
                info.add("maxTransceiveLength: " + nfcFTag.getMaxTransceiveLength());
                break;

            case "NfcV":
                info.add("aka ISO 15693");
                
                NfcV nfcVTag = NfcV.get(tag);
                info.add("dsfId: " + nfcVTag.getDsfId());
                info.add("responseFlags: " + nfcVTag.getResponseFlags());
                info.add("maxTransceiveLength: " + nfcVTag.getMaxTransceiveLength());
                break;

            case "Ndef":
                Ndef ndefTag = Ndef.get(tag);
                NdefMessage ndefMessage = null;

                try {
                    ndefTag.connect();
                    ndefMessage = ndefTag.getNdefMessage();
                    ndefTag.close();

                    for (final NdefRecord record : ndefMessage.getRecords()) {
                        final String id = record.getId().length == 0 ? "null" : Utils.bytesToHex(record.getId());
                        info.add("record[" + id + "].tnf: " + record.getTnf());
                        info.add("record[" + id + "].type: " + Utils.bytesToHexAndString(record.getType()));
                        info.add("record[" + id + "].payload: " + Utils.bytesToHexAndString(record.getPayload()));
                    }

                    info.add("messageSize: " + ndefMessage.getByteArrayLength());

                } catch (final Exception e) {
                    e.printStackTrace();
                    info.add("error reading message: " + e.toString());
                }

                HashMap<String, String> typeMap = new HashMap<String, String>();
                typeMap.put(Ndef.NFC_FORUM_TYPE_1, "typically Innovision Topaz");
                typeMap.put(Ndef.NFC_FORUM_TYPE_2, "typically NXP MIFARE Ultralight");
                typeMap.put(Ndef.NFC_FORUM_TYPE_3, "typically Sony Felica");
                typeMap.put(Ndef.NFC_FORUM_TYPE_4, "typically NXP MIFARE Desfire");

                String type = ndefTag.getType();
                if (typeMap.get(type) != null) {
                    type += " (" + typeMap.get(type) + ")";
                }
                info.add("type: " + type);

                info.add("canMakeReadOnly: " + ndefTag.canMakeReadOnly());
                info.add("isWritable: " + ndefTag.isWritable());
                info.add("maxSize: " + ndefTag.getMaxSize());
                break;

            case "NdefFormatable":
                info.add("nothing to read");

                break;

            case "MifareUltralight":
                MifareUltralight mifareUltralightTag = MifareUltralight.get(tag);
                info.add("type: " + mifareUltralightTag.getType());
                info.add("tiemout: " + mifareUltralightTag.getTimeout());
                info.add("maxTransceiveLength: " + mifareUltralightTag.getMaxTransceiveLength());
                break;

            case "IsoDep":
                info.add("aka ISO 14443-4");

                IsoDep isoDepTag = IsoDep.get(tag);
                info.add("historicalBytes: " + Utils.bytesToHexAndString(isoDepTag.getHistoricalBytes()));
                info.add("hiLayerResponse: " + Utils.bytesToHexAndString(isoDepTag.getHiLayerResponse()));
                info.add("timeout: " + isoDepTag.getTimeout());
                info.add("extendedLengthApduSupported: " + isoDepTag.isExtendedLengthApduSupported());
                info.add("maxTransceiveLength: " + isoDepTag.getMaxTransceiveLength());
                break;

            default:
                info.add("unknown tech!");
        }

        return info;
    }
}