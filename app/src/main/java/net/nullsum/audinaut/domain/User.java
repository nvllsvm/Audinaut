/*
  This file is part of Subsonic.
    Subsonic is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    Subsonic is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
    Copyright 2014 (C) Scott Jackson
*/

package net.nullsum.audinaut.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class User implements Serializable {
    public static final List<String> ROLES = new ArrayList<>();
    private static final String ADMIN = "adminRole";
    private static final String SETTINGS = "settingsRole";
    private static final String DOWNLOAD = "downloadRole";
    private static final String UPLOAD = "uploadRole";
    private static final String COVERART = "coverArtRole";
    private static final String COMMENT = "commentRole";
    private static final String STREAM = "streamRole";

    static {
        ROLES.add(ADMIN);
        ROLES.add(SETTINGS);
        ROLES.add(STREAM);
        ROLES.add(DOWNLOAD);
        ROLES.add(UPLOAD);
        ROLES.add(COVERART);
        ROLES.add(COMMENT);
    }

    private List<Setting> musicFolders;

    public User() {

    }

    public void addMusicFolder(MusicFolder musicFolder) {
        if (musicFolders == null) {
            musicFolders = new ArrayList<>();
        }

        musicFolders.add(new MusicFolderSetting(musicFolder.getId(), musicFolder.getName()));
    }

    public List<Setting> getMusicFolderSettings() {
        return musicFolders;
    }

    public static class Setting implements Serializable {
        private String name;
        private Boolean value;

        public Setting(String name, Boolean value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Boolean getValue() {
            return value;
        }

        public void setValue(Boolean value) {
            this.value = value;
        }
    }

    public static class MusicFolderSetting extends Setting {
        private final String label;

        public MusicFolderSetting(String name, String label) {
            super(name, false);
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
