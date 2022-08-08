package constants

type TaskType int
type QueueType int

const (
	// QueueTypeInMemory in memory queue type
	QueueTypeInMemory = "in-memory"
)

const (
	TaskStartID      = "START"
	TaskEndID        = "END"
	TaskNormalStatus = 1
)

// task instance status
const (
	TaskInstanceWaitStatus    = 1
	TaskInstanceProcessStatus = 2
	TaskInstanceSuccessStatus = 3
	TaskInstanceFailStatus    = 4
)

// workflow instance status
const (
	WorkflowInstanceProcessStatus = 1
	WorkflowInstanceSuccessStatus = 2
)

// log name
const (
	LogSchedule = "schedule"
	LogQueue    = "queue"
)

const (
	TaskTypeOperation = "operation"
	TaskTypeEvent     = "event"
	TaskTypeSwitch    = "switch"
)

const (
	OrderDesc = "DESC"
)
