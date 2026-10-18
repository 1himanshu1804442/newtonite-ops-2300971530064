package com.newtonite.ops.seed;

import com.newtonite.ops.model.entity.*;
import com.newtonite.ops.model.enums.*;
import com.newtonite.ops.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds initial operational datasets (4 teams, 12 users, memberships, and 52 work items)
 * upon application startup if the database is currently empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final WorkItemRepository workItemRepository;
    private final ActivityEventRepository activityEventRepository;
    private final OutboxJobRepository outboxJobRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (teamRepository.count() > 0) {
            log.info("Database already contains seed data. Skipping DataSeeder execution.");
            return;
        }

        log.info("Starting Newtonite Ops database seeding pipeline...");

        // 1. Seed Teams
        List<Team> teams = seedTeams();

        // 2. Seed Users
        List<User> users = seedUsers();

        // 3. Seed Team Memberships
        seedMemberships(teams, users);

        // 4. Seed Work Items & Activity Audit Events
        seedWorkItems(teams, users);

        // 5. Seed Initial Outbox Job records for dashboard observability
        seedOutboxJobs();

        log.info("Database seeding pipeline completed successfully. Ready for operations.");
    }

    private List<Team> seedTeams() {
        log.info("Seeding 4 operational engineering teams...");
        List<Team> teams = List.of(
                Team.builder()
                        .name("Platform Engineering")
                        .code("ENG-PLAT")
                        .description("Core infrastructure, CI/CD pipelines, Kubernetes runtime, and internal developer platform services.")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                Team.builder()
                        .name("Site Reliability Engineering")
                        .code("SRE-CORE")
                        .description("24/7 reliability, latency optimization, SLO tracking, and critical incident response.")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                Team.builder()
                        .name("Security Operations")
                        .code("SEC-OPS")
                        .description("Vulnerability scanning, zero-trust access control, audit compliance, and threat mitigation.")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                Team.builder()
                        .name("Data Infrastructure")
                        .code("DATA-INFRA")
                        .description("Real-time event streaming pipelines, Kafka clusters, lakehouse storage, and analytics compute engines.")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build()
        );
        return teamRepository.saveAll(teams);
    }

    private List<User> seedUsers() {
        log.info("Seeding 12 users and operators...");
        List<User> users = List.of(
                User.builder()
                        .name("Alex Rivera")
                        .email("alex.rivera@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                User.builder()
                        .name("Elena Rostova")
                        .email("elena.rostova@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150")
                        .createdAt(LocalDateTime.now().minusDays(85))
                        .build(),
                User.builder()
                        .name("Marcus Chen")
                        .email("marcus.chen@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150")
                        .createdAt(LocalDateTime.now().minusDays(80))
                        .build(),
                User.builder()
                        .name("Sarah Connor")
                        .email("sarah.connor@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                User.builder()
                        .name("Vikram Patel")
                        .email("vikram.patel@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150")
                        .createdAt(LocalDateTime.now().minusDays(75))
                        .build(),
                User.builder()
                        .name("Chloe Martin")
                        .email("chloe.martin@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150")
                        .createdAt(LocalDateTime.now().minusDays(70))
                        .build(),
                User.builder()
                        .name("David Kim")
                        .email("david.kim@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                User.builder()
                        .name("Priya Sharma")
                        .email("priya.sharma@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150")
                        .createdAt(LocalDateTime.now().minusDays(65))
                        .build(),
                User.builder()
                        .name("James Wilson")
                        .email("james.wilson@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150")
                        .createdAt(LocalDateTime.now().minusDays(60))
                        .build(),
                User.builder()
                        .name("Aisha Diallo")
                        .email("aisha.diallo@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150")
                        .createdAt(LocalDateTime.now().minusDays(90))
                        .build(),
                User.builder()
                        .name("Liam O'Connor")
                        .email("liam.oconnor@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150")
                        .createdAt(LocalDateTime.now().minusDays(55))
                        .build(),
                User.builder()
                        .name("Zoe Takahashi")
                        .email("zoe.takahashi@newtonite.io")
                        .avatarUrl("https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150")
                        .createdAt(LocalDateTime.now().minusDays(50))
                        .build()
        );
        return userRepository.saveAll(users);
    }

    private void seedMemberships(List<Team> teams, List<User> users) {
        log.info("Assigning role memberships across teams...");
        Team plat = teams.get(0);
        Team sre = teams.get(1);
        Team sec = teams.get(2);
        Team data = teams.get(3);

        List<TeamMembership> memberships = List.of(
                // ENG-PLAT
                TeamMembership.builder().team(plat).user(users.get(0)).role(Role.ADMIN).build(),
                TeamMembership.builder().team(plat).user(users.get(1)).role(Role.LEAD).build(),
                TeamMembership.builder().team(plat).user(users.get(2)).role(Role.MEMBER).build(),

                // SRE-CORE
                TeamMembership.builder().team(sre).user(users.get(3)).role(Role.ADMIN).build(),
                TeamMembership.builder().team(sre).user(users.get(4)).role(Role.LEAD).build(),
                TeamMembership.builder().team(sre).user(users.get(5)).role(Role.MEMBER).build(),

                // SEC-OPS
                TeamMembership.builder().team(sec).user(users.get(6)).role(Role.ADMIN).build(),
                TeamMembership.builder().team(sec).user(users.get(7)).role(Role.LEAD).build(),
                TeamMembership.builder().team(sec).user(users.get(8)).role(Role.VIEWER).build(),

                // DATA-INFRA
                TeamMembership.builder().team(data).user(users.get(9)).role(Role.ADMIN).build(),
                TeamMembership.builder().team(data).user(users.get(10)).role(Role.LEAD).build(),
                TeamMembership.builder().team(data).user(users.get(11)).role(Role.MEMBER).build()
        );

        teamMembershipRepository.saveAll(memberships);
    }

    private void seedWorkItems(List<Team> teams, List<User> users) {
        log.info("Seeding 52 operational work items across diverse statuses and priorities...");

        String[][] workItemTemplates = {
                // Title, Description, Type, Priority, Status, TeamIndex, CreatorIndex, AssigneeIndex (-1 = null)
                // 1-10: ENG-PLAT
                {"Upgrade EKS production clusters to Kubernetes v1.30", "Perform non-disruptive rolling upgrade of worker nodes and control planes across us-east-1 and eu-west-1.", "TASK", "HIGH", "OPEN", "0", "0", "-1"},
                {"Investigate CI/CD pipeline latency spike on GitHub runners", "Docker build layer caching appears invalidated on multi-arch images causing 4x longer builds.", "INCIDENT", "CRITICAL", "IN_PROGRESS", "0", "1", "2"},
                {"Automate Terraform state lock recovery mechanism", "Add self-healing AWS DynamoDB lock release mechanism for timed-out deployment jobs.", "FEATURE", "MEDIUM", "AWAITING_APPROVAL", "0", "2", "1"},
                {"Patch OpenSSL CVE-2024-0727 in base container images", "Rebuild all Alpine and Ubuntu base golden images with patched OpenSSL binaries.", "BUG", "CRITICAL", "RESOLVED", "0", "1", "0"},
                {"Implement ephemeral preview environments on pull requests", "Provision lightweight K8s namespaces with Helm charts automatically when PR opened.", "FEATURE", "HIGH", "CLOSED", "0", "0", "2"},
                {"Deprecate legacy Jenkins worker cluster", "Migrate remaining 14 batch jobs from Jenkins VM pool to Kubernetes Argo Workflows.", "TASK", "LOW", "OPEN", "0", "1", "-1"},
                {"Fix Helm chart validation timeout in deployment script", "Deploy script crashes if pod readiness check exceeds 300 seconds without descriptive logs.", "BUG", "MEDIUM", "IN_PROGRESS", "0", "2", "1"},
                {"Audit cross-account IAM roles for CI/CD deployers", "Review least-privilege policies and expire unused temporary assumed role credentials.", "TASK", "HIGH", "AWAITING_APPROVAL", "0", "0", "1"},
                {"Configure Prometheus metrics exporter for BuildKit daemon", "Expose build queue depth, cache hit ratio, and disk consumption metrics.", "FEATURE", "LOW", "RESOLVED", "0", "2", "0"},
                {"Update container registry retention policy", "Purge untagged development container images older than 30 days to reclaim S3 storage.", "TASK", "LOW", "CLOSED", "0", "1", "2"},

                // 11-24: SRE-CORE
                {"High 504 Gateway Timeout rate on Payment API gateway", "Sudden saturation of upstream database connection pool during flash checkout promotion.", "INCIDENT", "CRITICAL", "IN_PROGRESS", "1", "3", "4"},
                {"Tune PostgreSQL connection pooler (PgBouncer) parameters", "Increase default pool size from 50 to 120 and configure transaction-level pooling mode.", "TASK", "HIGH", "OPEN", "1", "4", "-1"},
                {"Configure automated PagerDuty escalation policies for API latency", "Alert secondary on-call engineer if p99 latency exceeds 450ms for more than 3 minutes.", "TASK", "MEDIUM", "AWAITING_APPROVAL", "1", "5", "3"},
                {"Resolve memory leak in edge Envoy proxy instances", "HTTP/2 stream reset handling holds allocated memory buffers under specific keep-alive conditions.", "BUG", "HIGH", "RESOLVED", "1", "4", "3"},
                {"Post-mortem analysis for June 14 Database Failover", "Document root cause of read replica promotion delay and implement split-brain safeguards.", "TASK", "MEDIUM", "CLOSED", "1", "3", "4"},
                {"Conduct quarterly disaster recovery failover drill", "Simulate complete AWS us-east-1 regional outage and redirect DNS traffic to us-west-2.", "TASK", "HIGH", "OPEN", "1", "3", "-1"},
                {"Investigate intermittent packet loss on cross-region VPC peering", "Analyze flow logs for MTU mismatch or router queue drops between US and EU VPCs.", "INCIDENT", "HIGH", "IN_PROGRESS", "1", "4", "5"},
                {"Define Error Budget burn rate alerts for Core Checkout Service", "Implement Google SRE multi-window multi-burn-rate alerting via Prometheus rules.", "FEATURE", "MEDIUM", "AWAITING_APPROVAL", "1", "5", "4"},
                {"Remediate disk space exhaustion on Elasticsearch cluster", "Delete logstash indices older than 14 days and increase EBS volume size on hot tier.", "INCIDENT", "CRITICAL", "RESOLVED", "1", "3", "4"},
                {"Optimize OpenTelemetry collector memory footprint", "Enable batch processor with 8192 item queue to prevent OOM kills on high traffic bursts.", "BUG", "LOW", "CLOSED", "1", "4", "5"},
                {"Implement synthetic health probe for third-party payment gateway", "Run automated probe transaction every 60 seconds from 3 geographic regions.", "FEATURE", "MEDIUM", "OPEN", "1", "5", "-1"},
                {"Fix Nginx ingress buffer overflow on large cookie headers", "Increase client_header_buffer_size and large_client_header_buffers in ingress ConfigMap.", "BUG", "HIGH", "IN_PROGRESS", "1", "4", "3"},
                {"Validate backup restoration scripts for primary Postgres DB", "Run automated test restore into isolated sandbox environment and check data checksums.", "TASK", "MEDIUM", "RESOLVED", "1", "3", "4"},
                {"Draft On-Call Runbook for Redis Sentinel split-brain recovery", "Provide step-by-step triage guide for manual master election when quorum is degraded.", "TASK", "LOW", "CLOSED", "1", "5", "4"},

                // 25-38: SEC-OPS
                {"Critical RCE vulnerability in Apache Commons dependency", "Dependency-check flagged high severity CVE in order processing microservice.", "INCIDENT", "CRITICAL", "IN_PROGRESS", "2", "6", "7"},
                {"Enforce strict MFA hardware token requirement for AWS root access", "Decommission SMS-based MFA and enforce FIDO2 WebAuthn keys across all accounts.", "TASK", "HIGH", "OPEN", "2", "6", "-1"},
                {"Configure AWS GuardDuty automated remediation lambda", "Automatically isolate EC2 instances or revoke credentials flagged with malicious activity.", "FEATURE", "HIGH", "AWAITING_APPROVAL", "2", "7", "6"},
                {"Rotate compromised API keys for external SMS provider", "Regenerate Twilio production credentials and update secrets in HashiCorp Vault.", "INCIDENT", "CRITICAL", "RESOLVED", "2", "7", "6"},
                {"Conduct annual SOC2 Type II compliance audit preparation", "Collect access logs, deployment approvals, and vulnerability scanner reports for auditor.", "TASK", "MEDIUM", "CLOSED", "2", "6", "8"},
                {"Deploy Falco runtime security agent on production Kubernetes", "Detect unauthorized shell executions and namespace escapes in real-time.", "FEATURE", "HIGH", "OPEN", "2", "6", "-1"},
                {"Implement HashiCorp Vault dynamic database credentials", "Replace static Postgres passwords with ephemeral credentials that expire after 1 hour.", "FEATURE", "HIGH", "IN_PROGRESS", "2", "7", "8"},
                {"Remediate open S3 bucket policy detected in staging account", "Remove public read permissions and enable AWS S3 Block Public Access globally.", "BUG", "HIGH", "AWAITING_APPROVAL", "2", "6", "7"},
                {"Conduct external penetration test on customer authentication API", "Engage third-party ethical hacking firm to evaluate JWT and OAuth2 authorization flows.", "TASK", "MEDIUM", "RESOLVED", "2", "7", "6"},
                {"Patch DNS rebinding vulnerability in webhook dispatcher", "Validate and block private IP address ranges (RFC 1918) in outgoing HTTP requests.", "BUG", "HIGH", "CLOSED", "2", "8", "7"},
                {"Audit inactive employee accounts in Okta directory", "Deprovision 18 contractor accounts that have not logged in within 90 days.", "TASK", "LOW", "OPEN", "2", "6", "-1"},
                {"Integrate Trivy container image scanning into CI pipeline", "Block deployment of images with CRITICAL or HIGH severity CVEs without approved waiver.", "FEATURE", "MEDIUM", "IN_PROGRESS", "2", "7", "6"},
                {"Investigate anomalous login attempts from Tor exit nodes", "Review IP geo-location logs and configure Cloudflare WAF rule to block Tor traffic.", "INCIDENT", "MEDIUM", "RESOLVED", "2", "6", "7"},
                {"Update TLS certificate cipher suites across CloudFront distributions", "Disable deprecated TLS 1.0 and 1.1 protocols and weak CBC mode ciphers.", "TASK", "LOW", "CLOSED", "2", "7", "8"},

                // 39-52: DATA-INFRA
                {"Kafka partition consumer lag exceeding 100k messages", "Order fulfillment consumer group halted due to poison pill deserialization error.", "INCIDENT", "CRITICAL", "IN_PROGRESS", "3", "9", "10"},
                {"Implement dead letter queue (DLQ) for failed Kafka consumer events", "Route unparseable messages to DLQ topic and alert on Slack channel instead of crashing consumer.", "FEATURE", "HIGH", "OPEN", "3", "10", "-1"},
                {"Upgrade Kafka cluster brokers to version 3.7.0 (KRaft mode)", "Eliminate ZooKeeper dependency and enable KRaft metadata controller consensus.", "TASK", "HIGH", "AWAITING_APPROVAL", "3", "11", "9"},
                {"Fix schema registry backward compatibility enforcement bug", "Avro schema evolution allowed breaking field deletion due to lenient configuration.", "BUG", "HIGH", "RESOLVED", "3", "10", "9"},
                {"Tune Spark streaming job batch window for clickstream analytics", "Reduce micro-batch latency from 60 seconds to 10 seconds without increasing executor OOM.", "TASK", "MEDIUM", "CLOSED", "3", "9", "11"},
                {"Defragment PostgreSQL indexes on high-churn telemetry tables", "Run REINDEX CONCURRENTLY during low-traffic maintenance window to reduce table bloat.", "TASK", "MEDIUM", "OPEN", "3", "10", "-1"},
                {"Investigate Apache Flink checkpointing timeouts", "State backend rocksdb write stalls during S3 multipart upload under high backpressure.", "INCIDENT", "HIGH", "IN_PROGRESS", "3", "9", "10"},
                {"Implement data retention lifecycle rules in Apache Iceberg tables", "Expire snapshots older than 30 days and compact small ORC/Parquet data files.", "FEATURE", "MEDIUM", "AWAITING_APPROVAL", "3", "11", "10"},
                {"Remediate database lock contention on sequence generator", "Increase sequence cache allocation from 1 to 50 on high-throughput primary key sequences.", "BUG", "MEDIUM", "RESOLVED", "3", "10", "9"},
                {"Migrate Elasticsearch analytics cluster to OpenSearch 2.12", "Migrate 40 TB index data seamlessly using remote reindex API with zero query downtime.", "TASK", "MEDIUM", "CLOSED", "3", "9", "10"},
                {"Implement data masking on sensitive PII fields in analytics lakehouse", "Enforce column-level masking policies so raw credit card and SSN data is redacted.", "FEATURE", "HIGH", "OPEN", "3", "10", "-1"},
                {"Investigate slow query execution on customer metrics materialized view", "Add composite B-tree index on (account_id, event_timestamp DESC) to speed up query.", "BUG", "MEDIUM", "IN_PROGRESS", "3", "11", "10"},
                {"Configure automated replication lag alerts for Read Replicas", "Trigger warning alert if pg_stat_replication lag exceeds 50MB or 10 seconds.", "FEATURE", "LOW", "RESOLVED", "3", "9", "10"},
                {"Archive historical transactional audit logs to AWS S3 Glacier", "Transfer logs older than 365 days to Glacier Deep Archive to optimize storage costs.", "TASK", "LOW", "CLOSED", "3", "10", "11"}
        };

        List<WorkItem> savedItems = new ArrayList<>();
        List<ActivityEvent> activities = new ArrayList<>();

        int daysAgo = 30;

        for (String[] t : workItemTemplates) {
            String title = t[0];
            String desc = t[1];
            WorkItemType type = WorkItemType.valueOf(t[2]);
            Priority priority = Priority.valueOf(t[3]);
            Status status = Status.valueOf(t[4]);
            int teamIdx = Integer.parseInt(t[5]);
            int creatorIdx = Integer.parseInt(t[6]);
            int assigneeIdx = Integer.parseInt(t[7]);

            Team team = teams.get(teamIdx);
            User creator = users.get(creatorIdx);
            User assignee = (assigneeIdx >= 0) ? users.get(assigneeIdx) : null;

            LocalDateTime createdTime = LocalDateTime.now().minusDays(daysAgo).minusHours(daysAgo % 24);
            LocalDateTime updatedTime = createdTime.plusHours(4);

            WorkItem item = WorkItem.builder()
                    .title(title)
                    .description(desc)
                    .type(type)
                    .priority(priority)
                    .status(status)
                    .team(team)
                    .creator(creator)
                    .assignee(assignee)
                    .version(status == Status.CLOSED ? 3L : (status == Status.RESOLVED ? 2L : (status == Status.IN_PROGRESS ? 1L : 0L)))
                    .createdAt(createdTime)
                    .updatedAt(updatedTime)
                    .build();

            WorkItem saved = workItemRepository.save(item);
            savedItems.add(saved);

            // Seed initial activity event
            activities.add(ActivityEvent.builder()
                    .workItem(saved)
                    .actor(creator)
                    .actionType(ActionType.CREATED)
                    .description("Work item created with status " + Status.OPEN)
                    .previousState(null)
                    .newState(Status.OPEN.name())
                    .createdAt(createdTime)
                    .build());

            // If advanced status, add transition audit record
            if (status != Status.OPEN) {
                User actor = (assignee != null) ? assignee : creator;
                activities.add(ActivityEvent.builder()
                        .workItem(saved)
                        .actor(actor)
                        .actionType(ActionType.STATUS_CHANGED)
                        .description("Status transitioned to " + status)
                        .previousState(Status.OPEN.name())
                        .newState(status.name())
                        .createdAt(updatedTime)
                        .build());
            }

            daysAgo = (daysAgo > 1) ? daysAgo - 1 : 30;
        }

        activityEventRepository.saveAll(activities);
        log.info("Saved {} work items and {} activity audit events.", savedItems.size(), activities.size());
    }

    private void seedOutboxJobs() {
        log.info("Seeding sample transactional outbox records...");
        List<OutboxJob> initialJobs = List.of(
                OutboxJob.builder()
                        .eventType("SYSTEM_INITIALIZED")
                        .payload("{\"event\":\"SYSTEM_INITIALIZED\",\"status\":\"SUCCESS\",\"timestamp\":\"" + LocalDateTime.now() + "\"}")
                        .status(OutboxStatus.COMPLETED)
                        .attempts(1)
                        .createdAt(LocalDateTime.now().minusMinutes(30))
                        .lastAttemptAt(LocalDateTime.now().minusMinutes(29))
                        .build(),
                OutboxJob.builder()
                        .eventType("METRICS_HEARTBEAT")
                        .payload("{\"event\":\"METRICS_HEARTBEAT\",\"service\":\"newtonite-ops-backend\",\"nodes\":4}")
                        .status(OutboxStatus.COMPLETED)
                        .attempts(1)
                        .createdAt(LocalDateTime.now().minusMinutes(10))
                        .lastAttemptAt(LocalDateTime.now().minusMinutes(9))
                        .build()
        );
        outboxJobRepository.saveAll(initialJobs);
    }
}
