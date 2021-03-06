package com.custodela.machina.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "machina")
@Validated
public class MachinaProperties {
    private String contact;
    private String token;
    @NotNull @NotBlank
    private String bugTracker;
    private List<String> branches;
    private List<String> filterSeverity;
    private List<String> filterCwe;
    private List<String> filterCategory;
    private List<String> filterStatus;
    private String mitreUrl;
    private String wikiUrl;
    private String codebashUrl;
    private boolean breakBuild = false;
    private boolean incremental = false;
    private Integer webHookQueue = 100;
    private Integer scanResultQueue = 4;

    private Mail mail;

    public String getContact() {
        return this.contact;
    }

    public String getToken() {
        return this.token;
    }

    public @NotNull
    @NotBlank String getBugTracker() {
        return this.bugTracker;
    }

    public List<String> getBranches() {
        return this.branches;
    }

    public List<String> getFilterSeverity() {
        return this.filterSeverity;
    }

    public List<String> getFilterCwe() {
        return this.filterCwe;
    }

    public List<String> getFilterCategory() {
        return this.filterCategory;
    }

    public List<String> getFilterStatus() {
        return this.filterStatus;
    }

    public String getMitreUrl() {
        return this.mitreUrl;
    }

    public String getWikiUrl() {
        return this.wikiUrl;
    }

    public String getCodebashUrl() {
        return this.codebashUrl;
    }

    public Mail getMail() {
        return this.mail;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setBugTracker(@NotNull @NotBlank String bugTracker) {
        this.bugTracker = bugTracker;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public void setFilterSeverity(List<String> filterSeverity) {
        this.filterSeverity = filterSeverity;
    }

    public void setFilterCwe(List<String> filterCwe) {
        this.filterCwe = filterCwe;
    }

    public void setFilterCategory(List<String> filterCategory) {
        this.filterCategory = filterCategory;
    }

    public void setFilterStatus(List<String> filterStatus) {
        this.filterStatus = filterStatus;
    }

    public void setMitreUrl(String mitreUrl) {
        this.mitreUrl = mitreUrl;
    }

    public void setWikiUrl(String wikiUrl) {
        this.wikiUrl = wikiUrl;
    }

    public void setCodebashUrl(String codebashUrl) {
        this.codebashUrl = codebashUrl;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Integer getWebHookQueue() {
        return webHookQueue;
    }

    public void setWebHookQueue(Integer webHookQueue) {
        this.webHookQueue = webHookQueue;
    }

    public Integer getScanResultQueue() {
        return scanResultQueue;
    }

    public void setScanResultQueue(Integer scanResultQueue) {
        this.scanResultQueue = scanResultQueue;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    public boolean isBreakBuild() {
        return breakBuild;
    }

    public void setBreakBuild(boolean breakBuild) {
        this.breakBuild = breakBuild;
    }

    public static class Mail {
        private String host;
        private Integer port = 25;
        private String username;
        private String password;
        private boolean enabled = false;

        public String getHost() {
            return this.host;
        }

        public Integer getPort() {
            return this.port;
        }

        public String getUsername() {
            return this.username;
        }

        public String getPassword() {
            return this.password;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    }

}