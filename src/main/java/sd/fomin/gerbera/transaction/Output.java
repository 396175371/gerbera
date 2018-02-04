package sd.fomin.gerbera.transaction;

import sd.fomin.gerbera.constant.OpCodes;
import sd.fomin.gerbera.types.OpSize;
import sd.fomin.gerbera.util.ByteBuffer;
import sd.fomin.gerbera.types.ULong;
import sd.fomin.gerbera.types.VarInt;
import sd.fomin.gerbera.util.Base58CheckUtils;
import sd.fomin.gerbera.util.HexUtils;

import java.util.Arrays;
import java.util.List;

class Output {

    private final boolean mainNet;
    private final long satoshi;
    private final String destination;
    private final OutputType type;

    Output(boolean mainNet, long satoshi, String destination, OutputType type) {
        if (satoshi <= 0) {
            throw new IllegalArgumentException("Amount of satoshi must be a positive value");
        }
        this.mainNet = mainNet;
        this.satoshi = satoshi;
        this.destination = destination;
        this.type = type;
    }

    byte[] serializeForSigHash() {
        ByteBuffer serialized = new ByteBuffer();

        serialized.append(ULong.of(satoshi).asLitEndBytes());

        byte[] lockingScript = getLockingScript(mainNet, destination);
        serialized.append(VarInt.of(lockingScript.length).asLitEndBytes());
        serialized.append(lockingScript);

        return serialized.bytes();
    }

    void fillTransaction(Transaction transaction) {
        transaction.addHeader("   Output (" + type.getDesc() + ")");

        transaction.addData("      Satoshi", ULong.of(satoshi).toString());
        byte[] lockingScript = getLockingScript(mainNet, destination);
        transaction.addData("      Lock length", VarInt.of(lockingScript.length).toString());
        transaction.addData("      Lock", HexUtils.asString(lockingScript));
    }

    private byte[] getLockingScript(boolean mainNet, String destination) {
        validateDestinationAddress(mainNet, destination);

        byte[] decodedAddress = Base58CheckUtils.decode(destination);
        byte[] hash = Arrays.copyOfRange(decodedAddress, 1, decodedAddress.length);

        byte prefixP2PKH = mainNet ? (byte) 0x00 : (byte) 0x6F;
        byte prefixP2SH = mainNet ? (byte) 0x05 : (byte) 0xC4;

        ByteBuffer lockingScript = new ByteBuffer();
        if (decodedAddress[0] == prefixP2PKH) {
            //P2PKH
            lockingScript.append(OpCodes.DUP, OpCodes.HASH160);
            lockingScript.append(OpSize.ofInt(hash.length).getSize());
            lockingScript.append(hash);
            lockingScript.append(OpCodes.EQUALVERIFY, OpCodes.CHECKSIG);
        } else if (decodedAddress[0] == prefixP2SH) {
            //P2SH
            lockingScript.append(OpCodes.HASH160);
            lockingScript.append(OpSize.ofInt(hash.length).getSize());
            lockingScript.append(hash);
            lockingScript.append(OpCodes.EQUAL);
        } else {
            throw new IllegalStateException("Should never happen");
        }

        return lockingScript.bytes();
    }

    private void validateDestinationAddress(boolean mainNet, String destination) {
        if (destination == null || destination.isEmpty()) {
            throw new IllegalArgumentException("Destination address must not be empty");
        }

        List<Character> prefixP2PKH = mainNet ? Arrays.asList('1') : Arrays.asList('m', 'n');
        List<Character> prefixP2SH = Arrays.asList(mainNet ? '3' : '2');
        char prefix = destination.charAt(0);

        if (!prefixP2PKH.contains(prefix) && !prefixP2SH.contains(prefix)) {
            throw new IllegalArgumentException("Only destination addresses starting with " + prefixP2PKH + " (P2PKH) " +
                    "or " + prefixP2SH + " (P2SH) supported.");
        }
    }

    long getSatoshi() {
        return satoshi;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(destination).append(" ").append(satoshi).toString();
    }
}
