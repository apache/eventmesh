#ifndef __RMB_ERRNO_H__
#define __RMB_ERRNO_H__

#ifdef __cplusplus
extern "C" {
#endif

extern int *rmb_error(void) __attribute__((__const__));

#define rmb_errno (*rmb_error())

struct rmb_err_msg 
{
	int err_no;
	const char* err_msg;
};

#define RMB_MAX_ERR_NUMS  500
#define RMB_ERROR_BASE_BEGIN 10000

//错误码列表
#define RMB_ERROR_ARGV_NULL									10001							//参数为空
#define RMB_ERROR_ARGV_LEN_ERROR							10002							//参数长度错误
#define RMB_ERROR_MALLOC_FAIL								10003							//申请内存失败
#define RMB_ERROR_INIT_CONTEXT_FAIL							10004							//初始化context失败
#define RMB_ERROR_MSG_MISSING_PART							10005							//发送RMB消息必要字段缺少
#define RMB_ERROR_RR_INTERFACE_CAN_NOT_SEND_EVENT_MSG		10006							//RMB同步接口不允许发送异步消息
#define RMB_ERROR_EVENT_INTERFACE_CAN_NOT_SEND_RR_MSG		10007							//RMB异步接口不允许发送同步消息
#define RMB_ERROR_NOW_CAN_NOT_SEND_MSG						10008							//现在禁止发送消息
#define RMB_ERROR_QUEUE_FULL								10009							//该topic已queue满，不允许发送
#define RMB_ERROR_GSL_SERVICE_ID_NULL						10010							//GSL服务id为空
#define RMB_ERROR_GSL_SERVICE_ID_ERROR						10011							//GSL服务id路由失败
#define RMB_ERROR_GSL_SVR_ERROR								10012							//GSL服务器返回失败
#define RMB_ERROR_REQ_GSL_ERROR								10013							//请求GSL服务失败
#define RMB_ERROR_MSG_UUID_FAIL								10014							//RMB消息生成UUID失败
#define RMB_ERROR_MSG_SET_SYSTEMHEADER_FAIL					10015							//RMB消息初始化为SOLACE消息时设置systemHeader失败
#define RMB_ERROR_SEND_GET_SESSION_FAIL						10016							//发送消息时，获取可用session失败
#define RMB_ERROR_SEND_EVENT_MSG_FAIL						10017							//发送异步消息失败
#define RMB_ERROR_SEND_RR_MSG_FAIL							10018							//发送同步消息失败
#define RMB_ERROR_SEND_RR_MSG_TIMEOUT						10019							//发送同步消息超时
#define RMB_ERROR_REPLY_TO_NULL								10020							//RR请求消息缺少replyTo，不允许reply
#define RMB_ERROR_REPLY_FAIL								10021							//回复消息失败
#define RMB_ERROR_SESSION_CONNECT_FAIL						10022							//session连接失败--->连接session失败，请检查rmb配置文件是否正确
#define RMB_ERROR_SESSION_RECONNECT_FAIL					10023							//session重连失败
#define RMB_ERROR_SESSION_DESTORY_FAIL						10024							//session销毁失败
#define RMB_ERROR_SESSION_NUMS_LIMIT						10025							//该类型session连接数已达上限
#define RMB_ERROR_LISTEN_QUEUE_NOT_EXIST					10026							//监听queue失败，不存在该queue
#define RMB_ERROR_LISTEN_QUEUE_FAIL							10027							//监听queue失败
#define RMB_ERROR_FLOW_DESTORY_FAIL							10028							//flow销毁失败
#define RMB_ERROR_LISTEN_TOPIC_FAIL							10029							//监听topic失败
#define RMB_ERROR_INIT_MQ_FAIL								10030							//初始化消息通信MQ失败
#define RMB_ERROR_INIT_FIFO_FAIL							10031							//初始化通知FIFO失败
#define RMB_ERROR_INIT_UDP_FAIL								10032							//初始化UDP失败
#define RMB_ERROR_INIT_EPOLL_FAIL							10033							//初始化Epoll失败
#define RMB_ERROR_MQ_NUMS_LIMIT								10034							//添加MQ数量超过上限
#define RMB_ERROR_ENQUEUE_MQ_FAIL							10035							//消息入队MQ失败
#define RMB_ERROR_DEQUEUE_MQ_FAIL							10036							//消息出队MQ失败
#define RMB_ERROR_MSG_2_BUF_FAIL							10037							//RMB消息转为Buf失败
#define RMB_ERROR_BUF_2_MSG_FAIL							10038							//Buf转RMB消息失败
#define RMB_ERROR_MSG_SET_CONTENT_TOO_LARGE					10039							//RMB消息设置Content过大
#define RMB_ERROR_MSG_SET_APPHEADER_TOO_LARGE				10040							//RMB消息设置AppHeader过大
#define RMB_ERROR_MSG_TTL_0									10041							//RMB消息设置存活时间必须大于0
#define RMB_ERROR_RCV_MSG_GET_CONTENT_FAIL					10042							//RMB接收消息获取Content失败
#define RMB_ERROR_RCV_MSG_GET_BINARY_FAIL					10043							//RMB接收消息获取BinaryAttachment失败
#define RMB_ERROR_RCV_MSG_CONTENT_TOO_LARGE					10044							//RMB接收消息Content过大
#define RMB_ERROR_RCV_MSG_APPHEADER_TOO_LARGE				10045							//RMB接收消息AppHeader过大
#define RMB_ERROR_MANAGE_MSG_PKG_ERROR						10046							//RMB管理消息包格式错误
#define RMB_ERROR_MANAGE_MSG_PKG_CHECK_FAIL					10047							//RMB管理消息鉴权失败
#define RMB_ERROR_CONTEXT_CREATE_FAIL						10048							//CONTEXT创建失败
#define RMB_ERROR_FIFO_PARA_ERROR							10049							//FIFO参数错误
#define RMB_ERROR_SHM_PARA_ERROR							10050							//SHM参数错误
#define RMB_ERROR_SEND_FIFO_NOTIFY_ERROR					10051							//fifo发送通知失败
#define RMB_ERROR_FLOW_NUMS_LIMIT							10052							//flow数目限制
#define RMB_ERROR_MSG_GET_SYSTEMHEADER_ERROR				10053							//消息systemHeader错误
#define RMB_ERROR_RMB_MSG_2_SOLACE_MSG_ERROR				10054							//copy rmb msg 2 solace msg error
#define RMB_ERROR_SOLACE_MSG_2_RMB_MSG_ERROR				10055							//copy solace msg 2 rmb msg error
#define RMB_ERROR_NO_AVAILABLE_SESSION						10056							//没有可用的session
#define RMB_ERROR_NO_BROADCAST_SESSION						10057							//没有可用的添加广播的session
#define RMB_ERROR_NO_AVAILABLE_CONTEXT						10058							//没有可用的context
#define RMB_ERROR_SET_FLOW_PROPETY							10059							//设置flow属性错误
#define RMB_ERROR_ACK_MSG_FAIL								10060							//ack消息失败
#define RMB_ERROR_LOGIC_NOTIFY_INIT							10061							//logic进程notify初始化失败
#define RMB_ERROR_SESSION_ADD_FAIL							10062							//增加session失败
#define RMB_ERROR_WORKER_NUMS_LIMIT							10063							//worker数量限制
#define RMB_ERROR_BUF_NOT_ENOUGH							10064							//buf不够存储
#define RMB_ERROR_STOP_FLOW_ERROR							10065							//停止flow收消息失败
#define RMB_ERROR_DEQUEUE_MQ_EMPTY							10066							//消息队列出队为空
#define RMB_ERROR_REMOVE_TOPIC_FAIL							10067							//删除监听topic失败

//proxy add
#define RMB_NOT_REGISTER_WORKER								10068							//worker还未注册
#define RMB_SEND_MSG_ERROR_TYPE								10069							//发送消息错误的类型
#define RMB_MSG_DECODE_ERROR								10070							//worker到proxy消息decode错误
#define RMB_WORKER_BUF_FULL									10071							//worker满了

#define RMB_ERROR_CREATE_THREAD_FAIL						10072							//创建线程失败
#define RMB_ERROR_ENCODE_FAIL						        10073							//Encode失败
#define RMB_ERROR_DECODE_FAIL						        10074							//Decode失败
#define RMB_ERROR_RR_RSP_NOTIFY_ERROR						10075							//RR回包唤醒错误
#define RMB_ERROR_WORKER_REGISTER_ERROR						10076							//注册worker失败
#define RMB_ERROR_WORKER_WINDOW_FULL                        10077                           //worker发送队列已满；
#define RMB_ERROR_WORKER_PUT_FIFO_ERROR						10078							//put msg into fifo error

#define RMB_ERROR_START_CALL_FAIL							10079
#define RMB_ERROR_END_CALL_FAIL								10080
#define RMB_ERROR_ENTRY_FAIL								10081
#define RMB_ERROR_EXIT_FAIL									10082
#define RMB_ERROR_TOPIC_COUNT_TOO_LARGE					    10083							//流控比时，topic个数超过最大个数
#define RMB_ERROR_CLIENT_GOODBYE_TIMEOUT				    10084							//客户端退出超时

void init_error();

const char* get_rmb_last_error();

void rmb_reset_error();

#ifdef __cplusplus
}
#endif

#endif
