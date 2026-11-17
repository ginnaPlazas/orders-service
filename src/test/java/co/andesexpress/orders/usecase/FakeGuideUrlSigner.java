package co.andesexpress.orders.usecase;

import co.andesexpress.orders.domain.port.out.GuideUrlSignerPort;

import java.util.ArrayList;
import java.util.List;

class FakeGuideUrlSigner implements GuideUrlSignerPort {

    final List<String> signedKeys = new ArrayList<>();

    @Override
    public String presignedGetUrl(String guideKey) {
        signedKeys.add(guideKey);
        return "https://signed.example/" + guideKey + "?X-Amz-Signature=abc";
    }
}
