package com.custodela.machina.controller;

import com.custodela.machina.config.BitBucketProperties;
import com.custodela.machina.config.CxProperties;
import com.custodela.machina.config.JiraProperties;
import com.custodela.machina.config.MachinaProperties;
import com.custodela.machina.dto.*;
import com.custodela.machina.dto.bitbucketserver.Change;
import com.custodela.machina.dto.bitbucketserver.PullEvent;
import com.custodela.machina.dto.bitbucketserver.PushEvent;
import com.custodela.machina.exception.InvalidTokenException;
import com.custodela.machina.exception.MachinaRuntimeException;
import com.custodela.machina.service.MachinaService;
import com.custodela.machina.utils.ScanUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.beans.ConstructorProperties;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


@RestController
@RequestMapping(value = "/" )
public class BitbucketServerController {

    private static final String SIGNATURE = "X-Hub-Signature";
    private static final String EVENT = "X-Event-Key";
    private static final String PING = EVENT + "=diagnostics:ping";
    private static final String PUSH = EVENT + "=repo:refs_changed";
    private static final String MERGE = EVENT + "=pr:opened";
    private static final String HMAC_ALGORITHM = "HMACSha256";
    private static final String MERGE_COMMENT = "/projects/{project}/repos/{repo}/pull-requests/{id}/comments";
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BitbucketServerController.class);

    private final MachinaProperties machinaProperties;
    private final BitBucketProperties properties;
    private final CxProperties cxProperties;
    private final JiraProperties jiraProperties;
    private final MachinaService machinaService;
    private Mac hmac;

    @ConstructorProperties({"machinaProperties", "properties", "cxProperties", "jiraProperties", "machinaService"})
    public BitbucketServerController(MachinaProperties machinaProperties, BitBucketProperties properties, CxProperties cxProperties, JiraProperties jiraProperties, MachinaService machinaService) {
        this.machinaProperties = machinaProperties;
        this.properties = properties;
        this.cxProperties = cxProperties;
        this.jiraProperties = jiraProperties;
        this.machinaService = machinaService;
    }

    @PostConstruct
    public void init() throws NoSuchAlgorithmException, InvalidKeyException {
        // initialize HMAC with SHA1 algorithm and secret
        if(!ScanUtils.empty(properties.getWebhookToken())) {
            SecretKeySpec secret = new SecretKeySpec(properties.getWebhookToken().getBytes(CHARSET), HMAC_ALGORITHM);
            hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(secret);
        }
    }

    @PostMapping(value = "/{product}", headers = PING)
    public String pingEvent(@PathVariable("product") String product){
        log.info("Processing PING request");
        return "ok";
    }

    /**
     * Push Request event webhook submitted.
     */
    @PostMapping(value = "/{product}", headers = MERGE)
    public ResponseEntity<EventResponse> mergeRequest(
            @RequestBody String body,
            @PathVariable("product") String product,
            @RequestHeader(value = SIGNATURE) String signature,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "branch", required = false) List<String> branch,
            @RequestParam(value = "severity", required = false) List<String> severity,
            @RequestParam(value = "cwe", required = false) List<String> cwe,
            @RequestParam(value = "category", required = false) List<String> category,
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "exclude-files", required = false) List<String> excludeFiles,
            @RequestParam(value = "exclude-folders", required = false) List<String> excludeFolders,
            @RequestParam(value = "override", required = false) String override,
            @RequestParam(value = "bug", required = false) String bug
    ){
        verifyHmacSignature(body, signature);

        MachinaOverride o = null;
        ObjectMapper mapper = new ObjectMapper();
        PullEvent event;

        try {
            event = mapper.readValue(body, PullEvent.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new MachinaRuntimeException();
        }

        log.info("Processing BitBucket MERGE request");
        o = ScanUtils.getMachinaOverride(override);

        try {
            String app = event.getPullRequest().getFromRef().getRepository().getName();
            if(!ScanUtils.empty(application)){
                app = application;
            }
            BugTracker.Type bugType = BugTracker.Type.BITBUCKETSERVERPULL;
            if(!ScanUtils.empty(bug)){
                bugType = BugTracker.Type.valueOf(bug.toUpperCase());
            }
            ScanRequest.Product p = ScanRequest.Product.valueOf(product.toUpperCase());
            String currentBranch = event.getPullRequest().getFromRef().getDisplayId();
            String targetBranch = event.getPullRequest().getToRef().getDisplayId();;
            List<String> branches = new ArrayList<>();
            List<Filter> filters;

            if(!ScanUtils.empty(branch)){
                branches.addAll(branch);
            }
            else if(!ScanUtils.empty(machinaProperties.getBranches())){
                branches.addAll(machinaProperties.getBranches());
            }

            BugTracker bt = ScanUtils.getBugTracker(assignee, bugType, jiraProperties);

            if(!ScanUtils.empty(severity) || !ScanUtils.empty(cwe) || !ScanUtils.empty(category) || !ScanUtils.empty(status)){
                filters = ScanUtils.getFilters(severity, cwe, category, status);
            }
            else{
                filters = ScanUtils.getFilters(machinaProperties.getFilterSeverity(), machinaProperties.getFilterCwe(),
                        machinaProperties.getFilterCategory(), machinaProperties.getFilterStatus());
            }

            String gitUrl = properties.getUrl().concat("/scm/")
                    .concat(event.getPullRequest().getFromRef().getRepository().getProject().getKey().concat("/"))
                    .concat(event.getPullRequest().getFromRef().getRepository().getSlug()).concat(".git");

            String gitAuthUrl = gitUrl.replace("https://", "https://".concat(properties.getToken()).concat("@"));
            gitAuthUrl = gitAuthUrl.replace("http://", "http://".concat(properties.getToken()).concat("@"));
            String mergeEndpoint = properties.getUrl().concat(properties.getApiPath()).concat(MERGE_COMMENT);
            mergeEndpoint = mergeEndpoint.replace("{project}", event.getPullRequest().getToRef().getRepository().getProject().getKey());
            mergeEndpoint = mergeEndpoint.replace("{repo}", event.getPullRequest().getToRef().getRepository().getSlug());
            mergeEndpoint = mergeEndpoint.replace("{id}", event.getPullRequest().getId().toString());
            ScanRequest request = ScanRequest.builder()
                    .application(app)
                    .product(p)
                    .namespace(event.getPullRequest().getFromRef().getRepository().getProject().getKey().replaceAll(" ","_"))
                    .repoName(event.getPullRequest().getFromRef().getRepository().getName())
                    .repoUrl(gitUrl)
                    .repoUrlWithAuth(gitAuthUrl)
                    .repoType(ScanRequest.Repository.BITBUCKET)
                    .branch(currentBranch)
                    .mergeTargetBranch(targetBranch)
                    .mergeNoteUri(mergeEndpoint)
                    .refs(event.getPullRequest().getFromRef().getId())
                    .email(null)
                    .incremental(false) //todo handle incremental
                    .scanPreset(cxProperties.getScanPreset())
                    .excludeFolders(excludeFolders)
                    .excludeFiles(excludeFiles)
                    .bugTracker(bt)
                    .filters(filters)
                    .build();

            request = ScanUtils.overrideMap(request, o);

            if(branches.isEmpty() || branches.contains(targetBranch)) {
                machinaService.initiateAutomation(request);
            }

        }catch (IllegalArgumentException e){
            log.error("Error submitting Scan Request. Product option incorrect {}", product);
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EventResponse.builder()
                    .message("Error submitting Scan Request.  Product or Bugtracker option incorrect ".concat(product))
                    .success(false)
                    .build());
        }
        return ResponseEntity.status(HttpStatus.OK).body(EventResponse.builder()
                .message("Scan Request Successfully Submitted")
                .success(true)
                .build());

    }


    /**
     * Receive Push event submitted from Bitbucket
     */
    @PostMapping(value = "/{product}", headers = PUSH)
    public ResponseEntity<EventResponse> pushRequest(
            @RequestBody String body,
            @PathVariable("product") String product,
            @RequestHeader(value = SIGNATURE) String signature,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "branch", required = false) List<String> branch,
            @RequestParam(value = "severity", required = false) List<String> severity,
            @RequestParam(value = "cwe", required = false) List<String> cwe,
            @RequestParam(value = "category", required = false) List<String> category,
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "exclude-files", required = false) List<String> excludeFiles,
            @RequestParam(value = "exclude-folders", required = false) List<String> excludeFolders,
            @RequestParam(value = "override", required = false) String override,
            @RequestParam(value = "bug", required = false) String bug
    ){
        verifyHmacSignature(body, signature);

        MachinaOverride o = null;
        ObjectMapper mapper = new ObjectMapper();
        PushEvent event;

        try {
            event = mapper.readValue(body, PushEvent.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new MachinaRuntimeException();
        }

        try {
            //if override is provided, check if chars are more than 20 in length, implying base64 encoded json
            if(!ScanUtils.empty(override)){
                if(override.length() > 20){
                    log.info("Overriding attributes with Base64 encoded String");
                    String json = new String(Base64.getDecoder().decode(override));
                    o = mapper.readValue(json, MachinaOverride.class);
                }
                else{
                    //TODO download file
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new MachinaRuntimeException();
        }
        try {
            String app = event.getRepository().getName();
            if(!ScanUtils.empty(application)){
                app = application;
            }
            BugTracker.Type bugType = BugTracker.Type.valueOf(machinaProperties.getBugTracker().toUpperCase());
            if(!ScanUtils.empty(bug)){
                bugType = BugTracker.Type.valueOf(bug.toUpperCase());
            }

            ScanRequest.Product p = ScanRequest.Product.valueOf(product.toUpperCase());
            String currentBranch = event.getChanges().get(0).getRefId().split("/")[2];
            List<String> branches = new ArrayList<>();
            List<Filter> filters;

            if(!ScanUtils.empty(branch)){
                branches.addAll(branch);
            }
            else if(!ScanUtils.empty(machinaProperties.getBranches())){
                branches.addAll(machinaProperties.getBranches());
            }

            BugTracker bt = ScanUtils.getBugTracker(assignee, bugType, jiraProperties);
            if(!ScanUtils.empty(severity) || !ScanUtils.empty(cwe) || !ScanUtils.empty(category) || !ScanUtils.empty(status)){
                filters = ScanUtils.getFilters(severity, cwe, category, status);
            }
            else{
                filters = ScanUtils.getFilters(machinaProperties.getFilterSeverity(), machinaProperties.getFilterCwe(),
                        machinaProperties.getFilterCategory(), machinaProperties.getFilterStatus());
            }
            List<String> emails = new ArrayList<>();

            emails.add(event.getActor().getEmailAddress());
//            http://localhost:7990/scm/cus/dvwa.git

            String gitUrl = properties.getUrl().concat("/scm/")
                    .concat(event.getRepository().getProject().getKey().concat("/"))
                    .concat(event.getRepository().getSlug()).concat(".git");
            String gitAuthUrl = gitUrl.replace("https://", "https://".concat(properties.getToken()).concat("@"));
            gitAuthUrl = gitAuthUrl.replace("http://", "http://".concat(properties.getToken()).concat("@"));
            ScanRequest request = ScanRequest.builder()
                    .application(app)
                    .product(p)
                    .namespace(event.getRepository().getProject().getKey().replaceAll(" ","_"))
                    .repoName(event.getRepository().getName())
                    .repoUrl(gitUrl)
                    .repoUrlWithAuth(gitAuthUrl)
                    .repoType(ScanRequest.Repository.BITBUCKET)
                    .branch(currentBranch)
                    .refs(event.getChanges().get(0).getRefId())
                    .email(emails)
                    .incremental(false)
                    .scanPreset(cxProperties.getScanPreset())
                    .excludeFolders(excludeFolders)
                    .excludeFiles(excludeFiles)
                    .bugTracker(bt)
                    .filters(filters)
                    .build();

            request = ScanUtils.overrideMap(request, o);

            if(branches.isEmpty() || branches.contains(currentBranch)) {
                machinaService.initiateAutomation(request);
            }

        }catch (IllegalArgumentException e){
            log.error("Error submitting Scan Request. Product option incorrect {}", product);
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(EventResponse.builder()
                    .message("Error submitting Scan Request.  Product or Bugtracker option incorrect ".concat(product))
                    .success(false)
                    .build());
        }
        return ResponseEntity.status(HttpStatus.OK).body(EventResponse.builder()
                .message("Scan Request Successfully Submitted")
                .success(true)
                .build());
    }

    /** Validates the received body using the BB hook secret. */
    private void verifyHmacSignature(String message, String signature) {
        byte[] sig = hmac.doFinal(message.getBytes(CHARSET));
        String computedSignature = "sha256=" + DatatypeConverter.printHexBinary(sig);
        if (!computedSignature.equalsIgnoreCase(signature)) {
            throw new InvalidTokenException();
        }
        log.info("Signature verified");
    }

}
