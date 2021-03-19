# aggregate-service
this is a api aggregation service that implemented with scala language and libraries like fs2, cats effect and http4s.

## run instruction
to run this project you need a system with jvm and sbt installation. for ruuning go to the route of project and run command "sbt run".

## configuraion
the project hase an application.conf file that defines all configs about the project. the configs parameters are as follow:
- host: it defines the server address and port
- client urls: it includes are clients and api that the service will call them during it's life cycle.
- scheduler params: the service has a scheduler that buffers requests and send them out with the citerias. it hase two parameters. 
  - max-buffer-size that defines maximum buffer chunks that need to send out. 
  - max-time-period that defines maximum period that a request can be kept be before sending out from buffer.
- timeout params: to handle timeout for clients calls and also collect data from clients, there are two parameters. 
  - client timout that define timeout for client call. 
  - collect timeout that define the time is need to collect data from clients.
