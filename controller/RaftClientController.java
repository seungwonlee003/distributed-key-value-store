@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/client")
public class RaftClientController {
    private final RaftNode raftNode;
    private final RaftLogManager logManager;
    private final ReadOperationHandler readOperationHandler;
    
    @GetMapping("/get")
    public ResponseEntity<String> read(@RequestParam String key){
        String val = readOperationHandler.handleRead(key);
        return ResponseEntity.ok(val);
    }

    @PostMapping("/insert")
    public ResponseEntity<String> insert(@RequestParam String key, @RequestParam String value) {
        return handleWrite(key, value, LogEntry.Operation.INSERT, "Insert");
    }

    @PostMapping("/update")
    public ResponseEntity<String> update(@RequestParam String key, @RequestParam String value) {
        return handleWrite(key, value, LogEntry.Operation.UPDATE, "Update");
    }

    @PostMapping("/delete")
    public ResponseEntity<String> delete(@RequestParam String key) {
        return handleWrite(key, null, LogEntry.Operation.DELETE, "Delete");
    }

    private ResponseEntity<String> handleWrite(String key, String value, LogEntry.Operation op, String label) {
        if (raftNode.getRole() != Role.LEADER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not the leader");
        }

        LogEntry entry = new LogEntry(raftNode.getCurrentTerm(), key, value, op);
        boolean committed = logManager.handleClientRequest(entry);
        if (committed) {
            return ResponseEntity.ok(label + " committed");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(label + " failed (not committed or leadership lost)");
        }
    }
}
