# docker monitoring (container monitoring )
docker monitoring with use docker engine
# 1. host 기본 정보
가장 기본적인 docker engine 호출 시 들어오는 json 정보를 자바의 ObjectMapper를 사용 하여 list 형태로 가져온다. list 안의 정보들은 map 형태이기 때문에 key 값으로 모든 정보 를 가져올 수 있다. 그렇기 때문에 특별히 클래스를 만들지 않아도 프로그래밍이 가능하다.
java의 Runtime().getRuntime().availableProcessors() 호출 시 논리적 코어의 개수를 알 수 있는데 이 값을 2로 나눠줄 경우 물리 코어의 개수를 확인 할 수 있다.
java의 OperatingSystemMXBean()을 사용하면 호스트 메모리 정보를 가지고 올 수 있다. 모 든 물리적 메모리 크기에 페이지 교환시 사용되는 크기를 더하고 Mb 단위로 맞춘다.
# 2. 컨테이너 정보 (컨테이너 IP주소, Process ID, 컨테이너 이름)
읽어 드린 컨테이너 개수만큼 반복문을 수행하면서 각 컨테이너의 정보를 얻어올 수 있도록 하였다. /v1.41/containers/json 호출로 읽어드린 json 정보에서는 기본적으로 각 컨테이너 에 할당된 IP 주소, 컨테이너의 Process ID, 컨테이너 이름을 알 수 있다. 얻어낸 이름에서 substring()을 통해 형식에 맞도록 정보를 변경한다.
# 3. 컨테이너 정보(할당된 cpu 개수, 메모리 크기 )
위에서 얻은 컨테이너 이름을 바탕으로 /v1.41/containers/{id}/stats을 호출한다. 해당 호출 로 얻어낸 json 정보에서는 각 컨테이너에 대한 자원 상태를 알 수 있다. 위 정보는 json 배 열 형태가 아닌 하나의 json 정보이기 때문에 list 형태가 아닌 Map 형태로 정보를 가져온다.
“online_cpus”값을 key로 가져온 정보는 각 컨테이너에 할당된 cpu 개수이다. “memory_stats”를 key 값으로 가져온 정보는 각 컨테이너의 메모리 자원 정보이고 해당 json 정보에서 “limit”을 key 값으로 얻은 정보는 컨테이너에 할당된 메모리 크기이다. 해당 정보는 bit 값이기 때문에 MB로 변환하도록 한다.
# 4. 컨테이너 정보(CPU 사용률)
문서에 보면 stats를 통해 가져온 자원 정보를 계산하여 CPU 사용률을 알 수 있다.
하지만 precpu_stats의 json 정보를 보면 system_cpu_usage를 제공하지 않는다. 고로 계산 을 위해 해당 정보는 제외하고 계산한다. 위 계산식이 의미하는 것은 전체 시스템 수행에서 사용되는 cpu의 사용량을 계산하고 그 값에서 “total_usage” 즉 컨테이너의 cpu 사용량의 비율을 계산하는 것이다.
# 5. 컨테이너 정보(Memory)
메모리 정보도 기본적으로 계산하는 방법을 제공한다. 하지만 memory_stats.stats.cache를 제공하지 않는 경우가 있어 계산 시 제외하도록 한다. 앞서 측정한 컨테이너에 할당된 메모리 를 기준으로 컨테이너의 메모리 사용량을 가지고 와 그 비율을 계산하면 컨테이너의 메모리 사용률을 알 수 있다. 이 과정에서 단위를 맞추는 과정이 필요하다. 
# 6. 컨테이너 정보(네트워크)
네트워크 패킷에 대한 정보는 기본적으로 별도의 RxTx라는 클래스를 만들어 정보를 저장했 다. 해당 정보는 기본적으로 누적값의 형태이다. 그렇기 때문에 모니터링 주기마다 정보를 저 장하고 직전 주기에 측정한 값과 비교하여 그 차를 추출하도록 한다.
# 7. host 유휴 메모리, 유휴 CPU
컨테이너의 정보를 추출하는 작업들을 print라는 하나의 함수에서 수행하도록 하였다. main 함수에서 위 함수 호출 후 앞서 언급한 osBean 객체의 함수 호출로 유휴 메모리 크기를 가지 고 오도록 한다. 그리고 앞서 구한 CPU 용량에서 컨테이너에서 사용된 모든 CPU 사용 총량 을 빼서 유휴 cpu 사용량을 구하도록 한다.
  
# 결과
# 고찰
컨테이너 플랫폼은 상태 확인으로 이미지를 빌드하는 경우 많은 작업을 수행할 수 있지만, 상 황이 잘못되었을 때 사람이 개입하도록 지속적인 모니터링 및 경고가 필요하다. 많은 응용 프 로그램에서 컨테이너를 모니터링할 수 있도록 제공하고 있지만, 기본적인 정보를 호출하는 방 법을 익힐 수 있어 위 작업은 좋은 경험이 되었다. 하지만 완벽한 정보를 얻지 못하는 경우도 있어 개인적으로 아쉬웠다. 그리고 ObjectMapper를 사용하였지만, 사용자 정의 클래스를 선 언하여 사용하였다면 코드의 길이를 줄일 수 있고 가시성도 더 좋았을거라 생각해 이 부분이 조금 아쉽다. 그리고 위 작업을 컨테이너를 통해 수행할 수 있다면 더 완성도가 높다고 생각 한다. /v1.41/containers/json 호출을 각 컨테이너마다 수행하게 되는데 위 호출이 시간이 오래 걸려 모든 컨테이너의 정보를 얻는 작업이 시간이 오래 걸려 성능이 좋은 프로그램은 아 니라고 생각한다.
조사를 통해 알게 된 사실로는 현업에서 가장 많이 사용하는 모니터링 툴에는 Prometheus가 있다는 것이다. Prometheus는 Kubernetes 및 컨테이너 식 컨테이너 런타임의 동일한 기반 에서 모니터링하는 프로그램이다. Prometheus는 모니터링에서 모든 앱에 대해 동일한 유형 의 메트릭을 내보낼 수 있다고 한다.
