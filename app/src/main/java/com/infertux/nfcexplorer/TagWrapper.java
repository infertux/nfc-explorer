package com.infertux.nfcexplorer;

class TagWrapper {
    private String id;
    TagTechList techList = new TagTechList();

    public TagWrapper(final String tagId) {
        id = tagId;
    }

    public final String getId() {
        return id;
    }
}