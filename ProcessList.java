package com.example.demo.services;

import com.example.demo.models.Assignment;
import com.example.demo.models.Req;
import com.example.demo.repos.AssignRepo;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NonNull;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ProcessList {

    @Value("${freeRooms}")
    private int rooms;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessList.class);
    private static final ObjectMapper om = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static final String WAITLIST = "WAITLIST";
    public static final String P1_ASSIGNED = "P1_ASSIGNED";
    public static final String P2_ASSIGNED = "P2_ASSIGNED";
    public static final int PRIORITY_P1 = 1;
    public static final int PRIORITY_P2 = 2;
    public static final int PRIORITY_WAITLIST = 3;

    private final Deque<Integer> p1Rooms = new ConcurrentLinkedDeque<>();
    private final Deque<Integer> p2Rooms = new ConcurrentLinkedDeque<>();
    private final Deque<Integer> rejectedRooms = new ConcurrentLinkedDeque<>();

    private final ReadWriteLock snapshotLock = new ReentrantReadWriteLock();
    private List<Assignment> p1Assign = List.of();
    private List<Assignment> p2Assign = List.of();

    private final AssignRepo ar;

    public ProcessList(AssignRepo ar) {
        this.ar = ar;
    }

    static List<Req> getRequests() throws IOException {
        try (InputStream is = new ClassPathResource("sample.json").getInputStream()) {
            return om.readValue(is, new TypeReference<>() {});
        }
    }

    @Transactional
    public void runFullReassignment() throws IOException {
        p1Rooms.clear();
        p2Rooms.clear();
        rejectedRooms.clear();
        ar.deleteAllInBatch();

        List<Req> requests = getRequests();

        Predicate<Req> hubTurnTime = req -> req.getArr() != null && req.getDep() != null &&
                Duration.between(req.getArr(), req.getDep()).toMinutes() <= 120;
        Predicate<Req> hubTurn = req -> req.getOrigin() == null || req.getOrigin().trim().isEmpty();
        Predicate<Req> isP2 = hubTurn.or(hubTurnTime);

        Map<Boolean, List<Req>> splitRequests = requests.stream().collect(Collectors.partitioningBy(isP2));
        List<Req> p2Requests = splitRequests.get(true);
        List<Req> p1Requests = splitRequests.get(false);

        int p1RoomCount = rooms / 2;
        for (int i = 1; i <= p1RoomCount; i++)
            p1Rooms.add(i);
        for (int i = p1RoomCount + 1; i <= rooms; i++)
            p2Rooms.add(i);

        List<Assignment> p1Assigns = new ArrayList<>();
        List<Assignment> p2Assigns = new ArrayList<>();
        Deque<Req> p1Waitlist = new ArrayDeque<>();
        Deque<Req> p2Waitlist = new ArrayDeque<>();

        assignP1(p1Requests, p1Waitlist, p1Assigns);

        List<Req> downgradedP1s = processP1Rejections(p1Requests, p1Waitlist, p1Assigns);

        Deque<Req> p1StillWaiting = new ArrayDeque<>();
        assignP1WaitersToP2(p1Waitlist, p1StillWaiting, p2Assigns);

        assignP2(p2Requests, p2Waitlist, p2Assigns);

        processP2Rejections(p2Requests, p2Waitlist, p2Assigns);

        p1Waitlist.clear();
        p1Waitlist.addAll(p1StillWaiting);

        List<Assignment> waiting = buildWaitlistAssignments(p1Waitlist, p2Waitlist, downgradedP1s);

        if (!p1Assigns.isEmpty())
            ar.saveAll(p1Assigns);

        if (!p2Assigns.isEmpty())
            ar.saveAll(p2Assigns);

        if (!waiting.isEmpty())
            ar.saveAll(waiting);

        snapshotLock.writeLock().lock();
        try {
            p1Assign = List.copyOf(p1Assigns);
            p2Assign = List.copyOf(p2Assigns);
        } finally {
            snapshotLock.writeLock().unlock();
        }

        long p1Count = p1Assigns.stream().filter(a -> P1_ASSIGNED.equals(a.getStatus())).count();
        long p2Count = p2Assigns.stream().filter(a -> P2_ASSIGNED.equals(a.getStatus())).count();
        LOGGER.info("Assignments: P1 assigned: {}, P2 assigned: {}, Waitlisted: {}", p1Count, p2Count, waiting.size());
    }

    private void assignP1(@NonNull List<Req> requests, Deque<Req> p1Waitlist, List<Assignment> p1Assigns) {
        for (Req r : requests) {
            Integer room = pollAvailableRoom(p1Rooms);
            if (room != null)
                p1Assigns.add(new Assignment(P1_ASSIGNED, r.getId(), room, PRIORITY_P1));
            else
                p1Waitlist.add(r);
        }
    }

    private Integer pollAvailableRoom(@NonNull Deque<Integer> primaryPool) {
        Integer room = primaryPool.pollFirst();
        return room != null ? room : rejectedRooms.pollFirst();
    }

    private @NonNull List<Req> processP1Rejections(@NonNull List<Req> p1Requests, Deque<Req> p1Waitlist,
                                                   @NonNull List<Assignment> p1Assigns) {
        List<Req> downgraded = new ArrayList<>();
        Map<Integer, Assignment> assignmentMap = p1Assigns.stream()
                .collect(Collectors.toMap(Assignment::getRequestID, a -> a));

        for (Req req : p1Requests) {
            if (!req.isRejectP1())
                continue;

            Assignment existing = assignmentMap.get(req.getId());
            if (existing != null && P1_ASSIGNED.equals(existing.getStatus())) {
                Integer freedRoom = clearAssignment(existing);
                downgraded.add(req);
                reassignOrReleaseRoom(freedRoom, p1Waitlist, p1Assigns);
            } else if (p1Waitlist.remove(req))
                downgraded.add(req);
        }
        return downgraded;
    }

    private static Integer clearAssignment(@NonNull Assignment assignment) {
        Integer freedRoom = assignment.getRoomID();
        assignment.setStatus(WAITLIST);
        assignment.setRoomID(null);
        assignment.setPriority(PRIORITY_WAITLIST);
        return freedRoom;
    }

    private void reassignOrReleaseRoom(Integer room, @NonNull Deque<Req> waitlist,
                                       List<Assignment> assigns) {
        Req waiter = waitlist.pollFirst();
        if (waiter != null) {
            assigns.add(new Assignment(P1_ASSIGNED, waiter.getId(), room, PRIORITY_P1));
            LOGGER.info("Reassigned Room {} to waiter {}", room, waiter.getId());
        } else
            rejectedRooms.offerFirst(room);
    }

    private void assignP1WaitersToP2(@NonNull Deque<Req> p1Waitlist, Deque<Req> p1StillWaiting,
                                     List<Assignment> p2Assigns) {
        while (!p1Waitlist.isEmpty()) {
            Req r = p1Waitlist.pollFirst();
            Integer room = pollAvailableRoom(p2Rooms);
            if (room != null)
                p2Assigns.add(new Assignment(P2_ASSIGNED, r.getId(), room, PRIORITY_P2));
            else
                p1StillWaiting.add(r);
        }
    }

    private void assignP2(@NonNull List<Req> p2Requests,
                          Deque<Req> p2Waitlist,
                          List<Assignment> p2Assigns) {
        for (Req r : p2Requests) {
            Integer room = pollAvailableRoom(p2Rooms);
            if (room != null)
                p2Assigns.add(new Assignment(P2_ASSIGNED, r.getId(), room, PRIORITY_P2));
            else
                p2Waitlist.add(r);
        }
    }

    private void processP2Rejections(@NonNull List<Req> p2Requests,
                                     Deque<Req> p2Waitlist,
                                     @NonNull List<Assignment> p2Assigns) {
        Map<Integer, Assignment> assignmentMap = p2Assigns.stream()
                .collect(Collectors.toMap(Assignment::getRequestID, a -> a));

        for (Req req : p2Requests) {
            if (!req.isRejectP2())
                continue;

            Assignment existing = assignmentMap.get(req.getId());
            if (existing != null && P2_ASSIGNED.equals(existing.getStatus()))
                handleP2BatchRejection(existing, req, p2Waitlist, p2Assigns);
        }
    }

    private void handleP2BatchRejection(Assignment existing, Req req,
                                        Deque<Req> p2Waitlist, List<Assignment> p2Assigns) {
        Integer freedRoom = clearAssignment(existing);
        Integer newRoom = pollAvailableRoom(p2Rooms);

        if (newRoom != null) {
            existing.setStatus(P2_ASSIGNED);
            existing.setRoomID(newRoom);
            existing.setPriority(PRIORITY_P2);
            rejectedRooms.offerFirst(freedRoom);
            LOGGER.info("Reassigned P2 {} to new room {}", req.getId(), newRoom);
        } else {
            rejectedRooms.offerFirst(freedRoom);
            Req waiter = p2Waitlist.pollFirst();
            if (waiter != null) {
                Integer room = rejectedRooms.pollFirst();
                if (room != null) {
                    p2Assigns.add(new Assignment(P2_ASSIGNED, waiter.getId(), room, PRIORITY_P2));
                    LOGGER.info("Assigned rejected room {} to P2 waiter {}", room, waiter.getId());
                } else
                    p2Waitlist.offerFirst(waiter);
            }
        }
    }

    private static @NonNull List<Assignment> buildWaitlistAssignments(Deque<Req> p1Waitlist,
                                                                      Deque<Req> p2Waitlist,
                                                                      List<Req> downgradedP1s) {
        List<Assignment> waitlist = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();

        for (Deque<Req> deque : List.of(p1Waitlist, p2Waitlist))
            for (Req r : deque)
                if (addedIds.add(r.getId()))
                    waitlist.add(new Assignment(WAITLIST, r.getId(), null, PRIORITY_WAITLIST));

        for (Req r : downgradedP1s)
            if (addedIds.add(r.getId()))
                waitlist.add(new Assignment(WAITLIST, r.getId(), null, PRIORITY_WAITLIST));

        return waitlist;
    }

    @Transactional
    public void rejectP1(Integer requestId) {
        Optional<Assignment> opt = ar.findByRequestIDAndStatus(requestId, P1_ASSIGNED);
        if (opt.isEmpty()) {
            LOGGER.info("No active P1 assignment for request {}", requestId);
            return;
        }

        Assignment existing = opt.get();
        Integer freedRoom = existing.getRoomID();

        // Step 1: Try to get a DIFFERENT P1 room
        Integer otherP1Room = p1Rooms.pollFirst();
        if (otherP1Room != null) {
            existing.setRoomID(otherP1Room);
            existing.setPriority(PRIORITY_P1);
            ar.save(existing);

            // FIXED: Give freed room to waitlisted request
            promoteWaitlistToRoom(freedRoom, PRIORITY_P1);

            // FIXED: Update in-memory snapshot
            refreshP1Snapshot();

            LOGGER.info("Reassigned DIFFERENT P1 room {} to request {}", otherP1Room, requestId);
            return;
        }

        // Step 2: Try P2 downgrade
        Integer p2Room = pollAvailableRoom(p2Rooms);
        if (p2Room != null) {
            existing.setStatus(P2_ASSIGNED);
            existing.setRoomID(p2Room);
            existing.setPriority(PRIORITY_P2);
            ar.save(existing);

            // FIXED: Give freed P1 room to waitlisted request
            promoteWaitlistToRoom(freedRoom, PRIORITY_P1);

            // FIXED: Update both snapshots
            refreshP1Snapshot();
            refreshP2Snapshot();

            LOGGER.info("Downgraded P1 request {} to P2 room {}", requestId, p2Room);
            return;
        }

        // Step 3: Waitlist
        existing.setStatus(WAITLIST);
        existing.setRoomID(null);
        existing.setPriority(PRIORITY_WAITLIST);
        ar.save(existing);

        // FIXED: Give freed room to another waiter
        promoteWaitlistToRoom(freedRoom, PRIORITY_P1);

        // FIXED: Update snapshot
        refreshP1Snapshot();

        LOGGER.info("Moved P1 request {} to waitlist", requestId);
    }

    @Transactional
    public void rejectP2(Integer requestId) {
        Optional<Assignment> opt = ar.findByRequestIDAndStatus(requestId, P2_ASSIGNED);
        if (opt.isEmpty()) {
            LOGGER.info("No active P2 assignment for request {}", requestId);
            return;
        }

        Assignment existing = opt.get();
        Integer freedRoom = existing.getRoomID();

        // Step 1: Try to get a DIFFERENT P2 room
        Integer otherP2Room = p2Rooms.pollFirst();
        if (otherP2Room != null) {
            existing.setRoomID(otherP2Room);
            existing.setPriority(PRIORITY_P2);
            ar.save(existing);

            // FIXED: Give freed room to waitlisted request
            promoteWaitlistToRoom(freedRoom, PRIORITY_P2);

            // FIXED: Update snapshot
            refreshP2Snapshot();

            LOGGER.info("Reassigned DIFFERENT P2 room {} to request {}", otherP2Room, requestId);
            return;
        }

        // Step 2: Waitlist (P2 doesn't downgrade further)
        existing.setStatus(WAITLIST);
        existing.setRoomID(null);
        existing.setPriority(PRIORITY_WAITLIST);
        ar.save(existing);

        // FIXED: Give freed room to another waiter
        promoteWaitlistToRoom(freedRoom, PRIORITY_P2);

        // FIXED: Update snapshot
        refreshP2Snapshot();

        LOGGER.info("Moved P2 request {} to waitlist", requestId);
    }

    // FIXED: New helper to promote waitlisted requests
    private void promoteWaitlistToRoom(Integer room, int priorityClass) {
        Optional<Assignment> waiterOpt = ar.findFirstWaitlistedByPriority(priorityClass);
        if (waiterOpt.isPresent()) {
            Assignment waiter = waiterOpt.get();
            String status = (priorityClass == PRIORITY_P1) ? P1_ASSIGNED : P2_ASSIGNED;
            waiter.setStatus(status);
            waiter.setRoomID(room);
            waiter.setPriority(priorityClass);
            ar.save(waiter);
            LOGGER.info("Promoted waitlisted request {} to {} room {}",
                    waiter.getRequestID(), status, room);
        } else {
            // No waiter - return room to appropriate pool
            rejectedRooms.offerFirst(room);
        }
    }

    // FIXED: Snapshot refresh helpers
    private void refreshP1Snapshot() {
        List<Assignment> fresh = ar.findAllByStatus(P1_ASSIGNED);
        snapshotLock.writeLock().lock();
        try {
            p1Assign = List.copyOf(fresh);
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    private void refreshP2Snapshot() {
        List<Assignment> fresh = ar.findAllByStatus(P2_ASSIGNED);
        snapshotLock.writeLock().lock();
        try {
            p2Assign = List.copyOf(fresh);
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    public List<Assignment> getP1() {
        snapshotLock.readLock().lock();
        try {
            return p1Assign;
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    public List<Assignment> getP2() {
        snapshotLock.readLock().lock();
        try {
            return p2Assign;
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    public List<Assignment> getWaitlist() {
        return ar.findWaitlistInOrder();
    }
}

@Component
class AssignmentInitializer implements ApplicationRunner {
    private final ProcessList processList;

    AssignmentInitializer(ProcessList processList) {
        this.processList = processList;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        processList.runFullReassignment();
    }
}