package com.example.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.slf4j.*;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SR1 {

  private static final int ROOMS = 30;
  private static final Logger LOGGER = LoggerFactory.getLogger(SR1.class);
  private static final ObjectMapper om = createObjectMapper();
  public static final String WAITLIST = "WAITLIST";
  public static final String P1_ASSIGNED = "P1_ASSIGNED";
  public static final String P2_ASSIGNED = "P2_ASSIGNED";
  private static final Set<Assignment> assigned = Collections.synchronizedSet(new LinkedHashSet<>());
  private static final BlockingDeque<Integer> freeRooms = new LinkedBlockingDeque<>();

  private final AssignmentRepository assignmentRepository;

  public SR1(AssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

  @PostConstruct
  public void init() {
    try {
      runAssignmentProcess();
    } catch (IOException e) {
      LOGGER.error("Failed to run assignment process on startup", e);
    }
  }

  @Nonnull
  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  static List<Req> getRequests() throws IOException {
    ClassPathResource resource = new ClassPathResource("sample.json");
    try (InputStream is = resource.getInputStream()) {
      return om.readValue(is, new TypeReference<>() {
      });
    }
  }

  public static void assignP1(List<Req> requests, Deque<Req> p1Waitlist) {
    for (Req r : requests) {
      r.setPrior(1);
      Integer room = freeRooms.pollFirst();
      if (room != null)
        assigned.add(new Assignment(P1_ASSIGNED, r.getId(), room));
      else {
        assigned.add(new Assignment(WAITLIST, r.getId(), null));
        p1Waitlist.add(r);
      }
    }
  }

  public static List<Req> processP1RejectionsStrict(List<Req> p1Requests, Deque<Req> p1Waitlist) {
    List<Req> downgraded = new ArrayList<>();
    Map<Integer, Assignment> assignmentMap = buildAssignmentMap();

    for (Req req : p1Requests) {
      if (!req.isRejectP1())
        continue;
      Assignment existing = assignmentMap.get(req.getId());
      if (isAssignedP1(existing))
        handleAssignedRejection(req, existing, p1Waitlist, assignmentMap, downgraded);
      else if (p1Waitlist.remove(req))
        downgraded.add(req);
    }
    return downgraded;
  }

  private static Map<Integer, Assignment> buildAssignmentMap() {
    synchronized (assigned) {
      return assigned.stream().collect(Collectors.toMap(Assignment::getRequestID, a -> a));
    }
  }

  private static boolean isAssignedP1(Assignment assignment) {
    return assignment != null && P1_ASSIGNED.equals(assignment.getStatus());
  }

  private static void handleAssignedRejection(Req req, Assignment existing, Deque<Req> p1Waitlist,
                                              Map<Integer, Assignment> assignmentMap, List<Req> downgraded) {
    Integer freedRoom = existing.getRoomID();

    existing.setStatus(WAITLIST);
    existing.setRoomID(null);
    downgraded.add(req);

    freedRoom = tryReassignToWaiter(freedRoom, p1Waitlist, assignmentMap);

    if (freedRoom != null)
      freeRooms.offerFirst(freedRoom);
  }

  private static Integer tryReassignToWaiter(Integer freedRoom, Deque<Req> p1Waitlist,
                                              Map<Integer, Assignment> assignmentMap) {
    if (p1Waitlist.isEmpty())
      return freedRoom;

    Req luckyP1 = p1Waitlist.pollFirst();
    Assignment luckyAssignment = assignmentMap.get(luckyP1.getId());

    if (luckyAssignment == null)
      return freedRoom;

    luckyAssignment.setStatus(P1_ASSIGNED);
    luckyAssignment.setRoomID(freedRoom);
    LOGGER.info("Reassigned Room {} to P1 Waiter {}", freedRoom, luckyP1.getId());
    return null;
  }

  public static void processP2(List<Req> sortedP2Queue) {
    Map<Integer, Assignment> assignmentMap;
    synchronized (assigned) {
      assignmentMap = assigned.stream().collect(Collectors.toMap(Assignment::getRequestID, a -> a));
    }

    for (Req r : sortedP2Queue) {
      Assignment existing = assignmentMap.get(r.getId());
      boolean isWaitlisted = existing != null && WAITLIST.equals(existing.getStatus());
      boolean isNew = existing == null;


      if ((isNew || isWaitlisted) && !freeRooms.isEmpty()) {
        Integer room = freeRooms.pollFirst();
        if (room != null) {
          if (existing != null) {
            existing.setStatus(P2_ASSIGNED);
            existing.setRoomID(room);
          } else
            assigned.add(new Assignment(P2_ASSIGNED, r.getId(), room));
        }
      } else if (isNew)
        assigned.add(new Assignment(WAITLIST, r.getId(), null));
    }
  }

  public void runAssignmentProcess() throws IOException {
    synchronized (assigned) {
      assigned.clear();
    }

    List<Req> requests = getRequests();

    final Predicate<Req> hubTurnTime = req -> req.getArr() != null && req.getDep() != null &&
        Duration.between(req.getArr(), req.getDep()).toMinutes() <= 120;
    final Predicate<Req> hubTurn = req -> req.getOrigin() == null || req.getOrigin().trim().isEmpty();
    final Predicate<Req> isP2 = hubTurn.or(hubTurnTime);

    Map<Boolean, List<Req>> splitReqs = requests.stream().collect(Collectors.partitioningBy(isP2));
    List<Req> p2Requests = splitReqs.get(true);
    List<Req> p1Requests = splitReqs.get(false);

    freeRooms.clear();
    for (int i = 1; i <= ROOMS; i++)
      freeRooms.add(i);

    Deque<Req> p1Waitlist = new ConcurrentLinkedDeque<>();
    assignP1(p1Requests, p1Waitlist);

    List<Req> downgradedP1s = processP1RejectionsStrict(p1Requests, p1Waitlist);

    List<Req> finalP2Queue = new ArrayList<>();
    finalP2Queue.addAll(downgradedP1s);
    finalP2Queue.addAll(p2Requests);
    finalP2Queue.addAll(p1Waitlist);

    processP2(finalP2Queue);

    List<Assignment> waiting = assigned.stream().filter(x->x.getStatus().equals(WAITLIST)).toList();
    assignmentRepository.saveAll(waiting);

    LOGGER.info("waitlists persisted to database");
  }

  public List<Assignment> getWaitList() {
    return assignmentRepository.findAll();
  }
}