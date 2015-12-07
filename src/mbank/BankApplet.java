package mbank;

import javacard.framework.*;
import sim.toolkit.*;
import sim.access.*;
import javacard.security.*;
import javacardx.crypto.*;

/**
* (c)2006 ubs121
*/
public class BankApplet extends javacard.framework.Applet implements ToolkitInterface, ToolkitConstants {

    public BankApplet() {
        // Create buffers
        bank = new byte[9];
        accref = new byte[9];
        key = JCSystem.makeTransientByteArray((short) 8, JCSystem.CLEAR_ON_RESET);
        sms = JCSystem.makeTransientByteArray((short) 141, JCSystem.CLEAR_ON_RESET);
        tempBuffer = JCSystem.makeTransientByteArray((short) 141, JCSystem.CLEAR_ON_RESET);
        // Init crypto
        cipherDES = Cipher.getInstance(Cipher.ALG_DES_CBC_ISO9797_M1, true);
        keyDES = KeyBuilder.buildKey(KeyBuilder.TYPE_DES_TRANSIENT_RESET, KeyBuilder.LENGTH_DES, true);

        reg = ToolkitRegistry.getEntry();
        bankid = reg.initMenuEntry(items[0].alpha, (short) 0, (short) items[0].alpha.length, PRO_CMD_SELECT_ITEM, false, (byte) 0, (short) 0);


        reg.setEvent(EVENT_FORMATTED_SMS_PP_ENV);
        reg.setEvent(EVENT_FORMATTED_SMS_PP_UPD);
        return;
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        (new BankApplet()).register();
    }

    public void processToolkit(byte event) {
        EnvelopeHandler envHdlr;
        envHdlr = EnvelopeHandler.getTheHandler();

        switch (event) {
            case EVENT_MENU_SELECTION:
                if (envHdlr.getItemIdentifier() == bankid) {
                    serviceBank();
                }
                break;
            case EVENT_FORMATTED_SMS_PP_ENV:
            case EVENT_FORMATTED_SMS_PP_UPD:
                envHdlr = EnvelopeHandler.getTheHandler();

                // Copy the user data in tempBuffer[]
                envHdlr.copyValue(envHdlr.getSecuredDataOffset(), tempBuffer, (short) 0, envHdlr.getSecuredDataLength());
                displayText(tempBuffer, (short) 0, envHdlr.getSecuredDataLength());
                break;
            default:
                break;
        }
    }

    public void serviceBank() throws ToolkitException {
        ProactiveResponseHandler prh = ProactiveResponseHandler.getTheHandler();

        currentItem = 'M'; // main menu is default
        if (bank[0] == 0) {
            currentItem = ' '; // jump to menu 'Toxirgoo'
        }
        // loop
        do {
            switch (currentItem) {
                case 'M':
                    // main menu
                    if (ShowMenu((Menu) items[currentItemIdx]) == 0) {
                        Util.arrayFillNonAtomic(sms, (short) 0, (short) sms.length, (byte) 0);
                        currentItem = prh.getItemIdentifier();
                        sms[0] = 1;
                        sms[1] = currentItem;
                    } else {
                        currentItem = FINAL_STATE;
                    }
                    break;
                case 'B':
                case 'S':
                    EncryptAndSend();
                    currentItem = 'M';
                    break;
                case 'P':
                case 'T':
                    if (GetInput(currentItem == 'P' ? dlgPayment : dlgTransfer) == 0) {
                        if (Confirm() == 0) {
                            EncryptAndSend();
                        }
                    }
                    currentItem = 'M';
                    break;
                case 'U':
                    // Top-Up
                    if (ShowMenu((Menu) items[3]) == 0) {
                        AppendUserData((byte) '+', prh.getItemIdentifier());
                        EncryptAndSend();
                    }

                    currentItem = 'M';
                    break;
                case 'I':
                    // Medeelel
                    if (GetInput((InputItem) items[27]) == 0) {
                        tempBuffer[0] = (byte) (prh.copyTextString(tempBuffer, (short)1) - 1);
                        AppendUserData((byte) '?', tempBuffer);

                        sendSMS();
                    }
                    currentItem = 'M';
                    break;
                case ' ':
                    // Toxirgoo
                    if (ShowMenu((Menu) items[7]) == 0) {
                        bank[0] = 1;
                        bank[1] = prh.getItemIdentifier();

                        if (bank[1] != 4) {
                            if (GetInput((InputItem) items[17]) == 0) {
                                accref[0] = (byte) prh.getTextStringLength();
                                prh.copyTextString(accref, (short) 1);
                            }
                        }
                    }
                    // change menu title into 'XXX Bank'
                    if (bank[0] > 0) {
                        switch (bank[1]) {
                            case (byte) 1:
                                // Anod
                                currentItemIdx = 36;
                                dlgTransfer = dlgTransfer_Def;
                                dlgPayment = dlgPayment_Def;
                                break;
                            case (byte) 2:
                                // Xaan
                                currentItemIdx = 37;
                                dlgTransfer = dlgTransfer_Xaan;
                                dlgPayment = dlgPayment_Xaan;
                                break;
                            case (byte) 3:
                                // Xac
                                currentItemIdx = 38;
                                dlgTransfer = dlgTransfer_Xac;
                                dlgPayment = dlgPayment_Xac;
                                break;
                            case (byte) 4:
                                // XXB
                                currentItemIdx = 39;
                                break;
                            default:
                                currentItemIdx = 0;
                                dlgTransfer = dlgTransfer_Def;
                                dlgPayment = dlgPayment_Def;
                                break;
                        }
                        reg.changeMenuEntry(bankid, items[currentItemIdx].alpha, (short) 0, (short) items[currentItemIdx].alpha.length, PRO_CMD_SELECT_ITEM, false, (byte) 0, (short) 0);
                    }
                    currentItem = 'M';
                    break;
                default:
                    currentItem = FINAL_STATE;
                    break;
            }
        } while (currentItem != FINAL_STATE);
    }

    public void process(APDU toProcess) {
    }

    private void AppendUserData(byte id, byte value) {
        sms[++sms[0]] = id;
        sms[++sms[0]] = 1;
        sms[++sms[0]] = value;
    }

    private void AppendUserData(byte id, byte[] value) {
        sms[++sms[0]] = id;
        sms[++sms[0]] = (value != null ? value[0] : 0);
        if (value != null) {
            sms[0] = (byte) (Util.arrayCopy(value, (short)1, 
                    sms, (short)(sms[0] + 1), (short)value[0]) - 1);
        }
    }

    private byte GetInput(byte[] dlg) {
        ProactiveResponseHandler prh = ProactiveResponseHandler.getTheHandler();
        i = 0;
        while (i < dlg.length && GetInput((InputItem) items[dlg[i]]) == 0) {
            tempBuffer[0] = (byte) (prh.copyTextString(tempBuffer, (short)1) - 1);
            AppendUserData(items[dlg[i]].id, tempBuffer);
            i++;
        }

        return (byte) (i >= dlg.length ? 0 : 1);
    }

    private byte GetInput(InputItem item) {
        ProactiveHandler ph = ProactiveHandler.getTheHandler();
        ph.initGetInput(item.type, DCS_8_BIT_DATA, item.alpha, (short) 0, (short) item.alpha.length, (short) 1, item.resLen);

        return ph.send();
    }

    private byte ShowMenu(Menu item) {
        ProactiveHandler ph = ProactiveHandler.getTheHandler();
        ph.init(PRO_CMD_SELECT_ITEM, (byte) 0, DEV_ID_ME);
        ph.appendTLV((byte) (TAG_ALPHA_IDENTIFIER | TAG_SET_CR), item.alpha, (short) 0, (short) item.alpha.length);
        for (i = 0; i < item.subItems.length; i++) {
            ph.appendTLV((byte) (TAG_ITEM | TAG_SET_CR), items[item.subItems[i]].id, items[item.subItems[i]].alpha, (short) 0, (short) items[item.subItems[i]].alpha.length);
        }
        return ph.send();
    }

    private byte Confirm() {
        i = 2;
        tempBuffer[0] = 1;
        while (i <= sms[0]) {
            j = 0;
            switch (sms[i]) {
                case '@':
                    j = 20;
                    break;
                case '$':
                    j = 24;
                    break;
                case '=':
                    j = 21;
                    break;
                case '~':
                    j = 26;
                    break;
                case '^':
                    j = 22;
                    break;
                case '>':
                    j = 23;
                    break;
            }
            if (j > 0) {
                InputItem item = (InputItem) items[j];
                tempBuffer[0] = (byte) Util.arrayCopy(item.alpha, (short)0, 
                        tempBuffer, (short)tempBuffer[0], (short)item.alpha.length);
                tempBuffer[tempBuffer[0]++] = (byte) ' ';
                tempBuffer[0] = (byte) Util.arrayCopy(sms, (short)(i + 2), 
                        tempBuffer, (short)tempBuffer[0], (short)sms[(byte)(i + 1)]);
                tempBuffer[tempBuffer[0]++] = (byte) '\n';
            }
            i += sms[(byte) (i + 1)] + 2;
        }
        return displayText(tempBuffer, (short) 1, (short) (tempBuffer[0] - 1));
    }

    private void EncryptAndSend() {
        if (GetInput((InputItem) items[18]) == 0) {
            ProactiveResponseHandler prh = ProactiveResponseHandler.getTheHandler();
            tempBuffer[0] = (byte) prh.getTextStringLength();
            prh.copyTextString(tempBuffer, (short) 1);
            // set key
            Util.arrayFillNonAtomic(key, (short) 0, (short) key.length, (byte) 0);
            Util.arrayCopy(tempBuffer, (short) 1, key, (short) 0, tempBuffer[0]);
            ((DESKey) (keyDES)).setKey(key, (short) 0);
            cipherDES.init(keyDES, Cipher.MODE_ENCRYPT);

            // append account settings
            AppendUserData((byte) '.', bank);
            if (bank[1] != 4) {
                AppendUserData((byte) '#', accref);
            }
            // append pin
            AppendUserData((byte) '*', tempBuffer);

            try {
                // encrypt
                Util.arrayCopy(sms, (short) 0, tempBuffer, (short) 0, (short) (sms[0] + 1));
                i = 2;
                j = 2;
                while (i <= tempBuffer[0]) {
                    switch (tempBuffer[i]) {
                        case '*':
                        case '@':
                        case '>':
                        case '$':
                            sms[j] = tempBuffer[i];
                            sms[(short) (j + 1)] = (byte) cipherDES.doFinal(tempBuffer, (short)(i + 2),
                                tempBuffer[(short)(i + 1)], sms, (short)(j + 2));
                            j += sms[(byte) (j + 1)] + 2;
                            break;
                        default:
                            j = Util.arrayCopy(tempBuffer, i, sms, j, (short) (tempBuffer[(byte)(i + 1)] + 2));
                            break;
                    }
                    i += tempBuffer[(short) (i + 1)] + 2;
                }
                sms[0] = (byte) (j - 1);

                // send a SMS
                sendSMS();
            } catch (CryptoException ex) {
                displayText(items[(byte) 30].alpha, (short) 0, (short) items[(byte)30].alpha.length);
            }
        }
    }

    private byte displayText(byte[] messageBuffer, short offset, short length) {
        // 0x00 - Display text automatically cleared after a delay
        // 0x80 - Display text cleared only after user action on mobile
        ProactiveHandler ph = ProactiveHandler.getTheHandler();
        ph.initDisplayText((byte) 0x80, DCS_8_BIT_DATA, messageBuffer, offset, length);
        return ph.send();
    }



    private byte sendSMS() {
        ProactiveHandler ph = ProactiveHandler.getTheHandler();

        // TP-UDH
        i = 0;
        tempBuffer[i++] = (byte) 0x11; // TP-MTI (http://www.dreamfabric.com/sms/)
        tempBuffer[i++] = (byte) 0x00; // TP-MR (http://www.dreamfabric.com/sms/)
        // Short number: 623=26F3
        tempBuffer[i++] = (byte) 0x03; // Address length
        tempBuffer[i++] = (byte) 0x81; // Unknown format  TON/NPI (http://www.dreamfabric.com/sms/type_of_address.html)
        tempBuffer[i++] = (byte) 0x26;
        tempBuffer[i++] = (byte) 0xF3;

        tempBuffer[i++] = (byte) 0x00; // 0x40 TP-PID (http://www.dreamfabric.com/sms/pid.html)
        tempBuffer[i++] = (byte) 0x04; // TP-DCS (http://www.dreamfabric.com/sms/dcs.html)
        tempBuffer[i++] = (byte) 0x12; // VP == 1 hour TP-VP (http://www.dreamfabric.com/sms/vp.html)
        // user data
        i = Util.arrayCopy(sms, (short) 0, tempBuffer, i, (short) (sms[0] + 1));

        ph.init(PRO_CMD_SEND_SHORT_MESSAGE, (byte) 0x00, DEV_ID_NETWORK);
        ph.appendTLV(TAG_ALPHA_IDENTIFIER, items[29].alpha, (short) 0, (short) items[29].alpha.length);
        ph.appendTLV((byte) (TAG_SMS_TPDU | TAG_SET_CR), tempBuffer, (short) 0, i);

        return ph.send();
    }


    // locals
    private final byte[] dlgTransfer_Def = {23, 21, 22, 20, 24, 25, 19};
    private final byte[] dlgPayment_Def = {26, 24, 25, 19};

    private final byte[] dlgTransfer_Xaan = {23, 20, 24, 25};
    private final byte[] dlgPayment_Xaan = {26, 24, 25};

    private final byte[] dlgTransfer_Xac = {23, 22, 20, 24, 25};
    private final byte[] dlgPayment_Xac = {26, 24, 25};


    private byte[] dlgTransfer = dlgTransfer_Def;
    private byte[] dlgPayment = dlgPayment_Def;

    private final Item[] items = new Item[] {
        // 00, Mobile Bank
        new Menu((byte)'M', new byte[] {'M','o','b','i','l','e',' ','B','a','n','k',' ',' ',' ',' '}, new byte[] {1, 2, 3, 4, 5, 6, 7}),
        // 01, Uldegdel
        new Item((byte)'B', new byte[] {'U','l','d','e','g','d','e','l'}),
        // 02, Tulbur
        new Item((byte)'P', new byte[] {'T','u','l','b','u','r'}),
        // 03, Top Up
        new Menu((byte)'U', new byte[] {'T','o','p',' ','U','p'}, new byte[] {8, 9, 10, 11, 12, 13, 14, 15, 16}),
        // 04, Guilgee
        new Item((byte)'T', new byte[] {'G','u','i','l','g','e','e'}),
        // 05, Guilgee shalgax
        new Item((byte)'S', new byte[] {'G','u','i','l','g','e','e',' ','s','h','a','l','g','a','x'}),
        // 06, Medeelel
        new Item((byte)'I', new byte[] {'M','e','d','e','e','l','e','l'}),
        // 07, Toxirgoo
        new Menu((byte)' ', new byte[] {'T','o','x','i','r','g','o','o'}, new byte[] {36, 37, 38, 39}),
        // 08, 500 negj
        new Item((byte)1, new byte[] {'5','0','0',' ','n','e','g','j'}),
        // 09, 1000 negj
        new Item((byte)2, new byte[] {'1','0','0','0',' ','n','e','g','j'}),
        // 10, 2500 negj
        new Item((byte)3, new byte[] {'2','5','0','0',' ','n','e','g','j'}),
        // 11, 6500 negj
        new Item((byte)4, new byte[] {'6','5','0','0',' ','n','e','g','j'}),
        // 12, 10000 negj
        new Item((byte)5, new byte[] {'1','0','0','0','0',' ','n','e','g','j'}),
        // 13, 8000 xon.negj
        new Item((byte)6, new byte[] {'8','0','0','0',' ','x','o','n','.','n','e','g','j'}),
        // 14, 15000 xon.negj
        new Item((byte)7, new byte[] {'1','5','0','0','0',' ','x','o','n','.','n','e','g','j'}),
        // 15, 15400 xon.negj
        new Item((byte)8, new byte[] {'1','5','4','0','0',' ','x','o','n','.','n','e','g','j'}),
        // 16, 21000 xon.negj
        new Item((byte)9, new byte[] {'2','1','0','0','0',' ','x','o','n','.','n','e','g','j'}),
        // 17, Dansnii ner?
        new InputItem((byte)'#', new byte[] {'D','a','n','s','n','i','i',' ','n','e','r',':'}, (byte)1,(byte)8),
        // 18, Nuuc kod?
        new InputItem((byte)'*', new byte[] {'N','u','u','c',' ','k','o','d',':'}, (byte)4,(byte)8),
        // 19, Tulxuur?
        new InputItem((byte)'!', new byte[] {'T','u','l','x','u','u','r',':'}, (byte)0,(byte)6),
        // 20, Dans?
        new InputItem((byte)'@', new byte[] {'D','a','n','s',':'}, (byte)0,(byte)18),
        // 21, Bank?
        new InputItem((byte)'=', new byte[] {'B','a','n','k',':'}, (byte)1,(byte)8),
        // 22, Salbar?
        new InputItem((byte)'^', new byte[] {'S','a','l','b','a','r',':'},(byte)0,(byte)4),
        // 23, Xuleen avagch?
        new InputItem((byte)'>', new byte[] {'X','u','l','e','e','n',' ','a','v','a','g','c','h',':'},(byte)1,(byte)15),
        // 24, Mungun dun?
        new InputItem((byte)'$', new byte[] {'M','u','n','g','u','n',' ','d','u','n',':'},(byte)0,(byte)8),
        // 25, Guilgeenii utga?
        new InputItem((byte)':', new byte[] {'G','u','i','l','g','e','e','n','i','i',' ','u','t','g','a',':'},(byte)1,(byte)25),
        // 26, Tulburiin kod?
        new InputItem((byte)'~', new byte[] {'T','u','l','b','u','r','i','i','n',' ','k','o','d',':'},(byte)1,(byte)8),
        // 27, Index?
        new InputItem((byte)'?', new byte[] {'I','n','d','e','x',':'},(byte)1,(byte)8),
        // 28, Ilgeex uu?
        new Item((byte)0, new byte[] {'I','l','g','e','e','x',' ','u','u','?'}),
        // 29, Mobile Bank...
        new Item((byte)0, new byte[] {'M','o','b','i','l','e',' ','B','a','n','k','.','.','.'}),
        // 30, Kodloxod aldaatai
        new Item((byte)0, new byte[] {'K','o','d','l','o','x','o','d',' ','a','l','d','a','a','t','a','i'}),
        // 31, Dans solix
        new Item((byte)2, new byte[] {'D','a','n','s',' ','s','o','l','i','x'}),
        // 32, Nuuc kod solix
        new Item((byte)2, new byte[] {'N','u','u','c',' ','k','o','d',' ','s','o','l','i','x'}),
        // 33, Mungunii negj
        new Menu((byte)'%', new byte[] {'M','u','n','g','u','n','i','i',' ','n','e','g','j',':'}, new byte[] {34, 35}),
        // 34, MNT
        new Item((byte)1, new byte[] {'M','N','T'}),
        // 35, USD
        new Item((byte)2, new byte[] {'U','S','D'}),
        // 36, Anod Bank
        new Menu((byte)1, new byte[] {'A','n','o','d',' ','B','a','n','k'}, new byte[] {1, 2, 3, 4, 5, 6, 7}),
        // 37, Xaan Bank
        new Menu((byte)2, new byte[] {'X','a','a','n',' ','B','a','n','k'}, new byte[] {1, 4, 2, 5, 6, 7}),
        // 38, Xac Bank
        new Menu((byte)3, new byte[] {'X','a','c',' ','B','a','n','k'}, new byte[] {1, 4, 2, 5, 6, 7}),
        // 39, XX Bank
        new Menu((byte)4, new byte[] {'X','X',' ','B','a','n','k'}, new byte[] {1,3,7})
    };


    // Locals
    private byte bankid;
    private byte currentItem = 'M';
    private byte currentItemIdx = 0;
    private short i;
    private short j;
    private ToolkitRegistry reg;

    private Cipher cipherDES;
    private Key keyDES;

    // Buffers
    private byte[] bank;
    private byte[] accref;
    private byte[] key;

    private byte[] sms;
    private byte[] tempBuffer;

    private final byte FINAL_STATE = 0;

    class Item {

        public byte id;
        public byte[] alpha;

        public Item(byte id, byte[] alpha) {
            this.id = id;
            this.alpha = alpha;
        }
    }

    class InputItem extends Item {

        public byte type;
        public byte resLen;


        public InputItem(byte id, byte[] alpha, byte type, byte resLen) {
            super(id, alpha);
            this.type = type;
            this.resLen = resLen;
        }
    }

    class Menu extends Item {

        public byte[] subItems;

        public Menu(byte id, byte[] alpha, byte[] subItems) {
            super(id, alpha);
            this.subItems = subItems;
        }
    }
}
