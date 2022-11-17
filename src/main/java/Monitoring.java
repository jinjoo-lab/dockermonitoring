import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONArray;

import java.io.*;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import org.json.simple.JSONObject;

import java.util.*;
// 네트워크에서 Rx byte , Rx packet,  Tx byte , Tx packet 정보를 확인하기 위한 클래스
class RxTx
{
    int RxByte;
    int RxPacket;
    int TxByte;
    int TxPacket;
    RxTx() // 기본 생성자 -> 처음 확인 시 0으로 전부 저장
    {
        this.RxByte=0;
        this.RxPacket=0;
        this.TxByte=0;
        this.TxPacket=0;
    }
    RxTx(int RxByte, int RxPacket, int TxByte, int TxPacket) // Rx, Tx 정보는 누적값이기 때문에 직전 값의 정보를 지속적으로 유지 필요
    {
        this.TxPacket=TxPacket;
        this.TxByte=TxByte;
        this.RxPacket= RxPacket;
        this.RxByte=RxByte;
    }

    @Override
    public String toString() {
        return "rx byte : "+this.RxByte+'\n' +
                "rx packet : "+this.RxPacket+'\n'+
                "tx byte : "+this.TxByte+'\n'+
                "tx packet : "+this.TxPacket+"\n";
    }
}

public class Monitoring {
    static RxTx[] packet_arr = new RxTx[1000]; // 충분히 큰 길이의 배열을 선언하여 각 컨테이너에 대한 네트워크 패킷 정보 유지
    static Long restMemory = 0l;
    static Long totalUseMemory = 0l;

    public static void main(String[] args) throws IOException, InterruptedException
    {
        String path = "/Users/jinjuwon/Documents/mac_git/demo/dockerhw/src/main/java/result.txt";
        JSONObject json = new JSONObject();
        ObjectMapper mapper = new ObjectMapper(); // json에 대한 정보를 별도의 클래스로 유지하는 방법도 있지만 자바의 ObjectMapper를 통해 클래스를 생성하지 않고도 확인 가능
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class); // 자바에서 기본 OS 정보 확인을 위해서는 osBean 객체를 활용해야 한다
        PrintWriter out = new PrintWriter(new FileWriter(path,true),true);
        while(true)
        {
            // Docker Engine 사용을 위한 코드
            Process process = Runtime.getRuntime().exec("curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String str;
            while((line=in.readLine())!=null)
            {
                List<Map<String,Object>> datas = mapper.readValue(line, new TypeReference<>() {}); // 정보에는 현재 동작중인 컨테이너의 json 정보가 json array 형태
                System.out.println("container num : "+ datas.size()); // List size가 곧 컨테이너의 개수
                json.put("container num",datas.size());
                int availableCore = (Runtime.getRuntime().availableProcessors() / 2) * 100;
                // Runtime.getRuntime().availableProcessors() -> 논리적 코어 개수이므로  /2를 하여 물리적 코어 개수 확인
                System.out.println("host cpu 정보 : "+ availableCore+"%");
                json.put("host cpu(%)",availableCore);
                // host 총 메모리 확인 시 사용하는 코드
                double total_memory2 = (osBean.getTotalPhysicalMemorySize() + osBean.getTotalSwapSpaceSize())/1024/1024;
                String memoryString = String.format("%.2f",total_memory2);
                System.out.println("호스트 메모리 크기 총량 : "+ memoryString + "MB");
                json.put("host memory(MB)",memoryString);

                // 컨테이너에 대한 각 정보를 출력하고 총 cpu 사용량을 반환하여 유휴 cpu %를 계산
                Double restCpu = availableCore - print(datas,json);

                // host 유휴 메모리 확인
                long host_rest_memory = (osBean.getFreePhysicalMemorySize() + osBean.getFreeSwapSpaceSize()) /1024/1024;
                String restMemoryString = host_rest_memory+"";
                System.out.println("호스트 유휴 메모리 : "+restMemoryString+"MB");
                json.put("host 유휴 memory",restMemoryString);
                System.out.println("호스트 유휴 CPU : "+restCpu+"%");
                json.put("host 유휴 CPU", restCpu);


                // json 로그 형태로 파일에 저장
                try (out) {
                    out.write(json.toString());
                    out.write("\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("---------------------------Monitor Sleep-------------------------------------------------------------------");
            Thread.sleep(2000); // 모니터링 주기 2초
            in.close();
        }
    }
    // 컨테이너에 대한 정보 확인
    public static double print(List<Map<String,Object>> datas,JSONObject json) throws IOException, InterruptedException
    {
        int length = datas.size(); // container 개수
        double CpuUse =0f; // 모든 컨테이너의 cpu 사용량을 더하여 총 cpu 사용량을 구한다

        int i=0;
        totalUseMemory=0l;
        for(Map<String,Object> data : datas) // 컨테이너 개수만큼 반복문 실행
        {

            Map<String,Object> first = (Map<String,Object>)data.get("NetworkSettings");
            Map<String,Object> second = (Map<String,Object>)first.get("Networks");
            Map<String,Object> IpAddress = (Map<String,Object>)second.get("bridge");
            System.out.println("----------------Container------------------");
            System.out.println("IpAddress : "+IpAddress.get("IPAddress")); // 컨테이너의 IP 주소
            json.put("IPAddress"+i ,IpAddress.get("IPAddress"));
            Thread.sleep(100);
            System.out.println("Process Id : "+data.get("Id")); // 컨테이너의 ID 확인
            json.put("Process Id"+i,data.get("Id"));
            Thread.sleep(100);

            String obj = data.get("Names").toString();
            obj = obj.substring(1,obj.length()-1); // 각 컨테이너의 이름으로 docker engine에서 stats를 호출
            System.out.println("container name : " + obj.substring(1,obj.length())); // 컨테이너의 이름 호출
            json.put("container name"+i,obj.substring(1,obj.length()));
            Thread.sleep(100);

            // 수행중인 컨테이너의 stats 정보 호출
            Process process = Runtime.getRuntime().exec("curl -s --unix-socket /var/run/docker.sock http://v1.41/containers"+obj+"/stats");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine() ;
            Map<String,Object> stats = new ObjectMapper().readValue(line, new TypeReference<>() {});
            Map<String,Object> cpu_stats = (Map<String,Object>)stats.get("cpu_stats");

            Long cpu_num = Long.parseLong(cpu_stats.get("online_cpus").toString()); // 컨테이너마다 할당된 cpu 개수 확인
            System.out.println("Cpu num : "+ cpu_stats.get("online_cpus"));
            json.put("CPU num"+i,cpu_stats.get("online_cpus"));
            Thread.sleep(100);

            Map<String,Object> memory_stats = (Map<String,Object>)stats.get("memory_stats");
            Long Climit = Long.parseLong(memory_stats.get("limit").toString()) / 8000000 ; // 호출 값이 bit -> byte 변환
            System.out.println("할당된 메모리 크기 : "+ Climit +"MB"); // 컨테이너마다 할당된 메모리 크기
            json.put("container memory"+i,Climit);
            Thread.sleep(100);
            // CPU 사용률
            // cpu_delta = cpu_stats.cpu_usage.total_usage - precpu_stats.cpu_usage.total_usage
            // system_cpu_delta = cpu_stats.system_cpu_usage - precpu_stats.system_cpu_usage
            // CPU usage % = (cpu_delta / system_cpu_delta) * number_cpus * 100.0

            Long CsystemCpuUsage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString()); // system 전체의 cpu 사용량 확인
            Map<String,Object> cpu_stats2 = (Map<String,Object>)cpu_stats.get("cpu_usage");
            Long CtotalCpuUsage = Long.parseLong(cpu_stats2.get("total_usage").toString()); // 컨테이너의 총 cpu 사용량 확인
            Map<String,Object> pre_cpu_stats = (Map<String,Object>)stats.get("precpu_stats");
            Map<String,Object> pre_cpu_stats2 = (Map<String,Object>)pre_cpu_stats.get("cpu_usage");
            Long PtotalCpuUsage = Long.parseLong(pre_cpu_stats2.get("total_usage").toString());

            Double cpu_percent = (CtotalCpuUsage - PtotalCpuUsage) * cpu_num * 100.0 / CsystemCpuUsage;
            String percent = String.format("%.5f",cpu_percent);
            CpuUse = cpu_percent;
            System.out.println("CPU 사용률 : "+ percent+"%");
            json.put("CPU usage"+i,percent);
            Thread.sleep(100);

            // 메모리 점유율, 사용중인 메모리
            // available_memory = memory_stats.limit
            // Memory usage % = (used_memory / available_memory) * 100.0
            // used_memory = memory_stats.usage - memory_stats.stats.cache

            // 하지만 cache 값을 포함하지 않는 경우가 있기 때문에 보편적인 실행을 위해 식을 아래와 같이 변형
            // 컨테이너를 실행 시 cache 값이 포함되지 않는 경우가 있습니다 !!
            // used_memory = memory_stats.usage

            Long usage = Long.parseLong(memory_stats.get("usage").toString());
            Map<String,Object> memory_stats2 = (Map<String, Object>) memory_stats.get("stats");
            // Long cache = Long.parseLong(memory_stats2.get("cache").toString());

            Long used_memory = usage ; // - cache;
            System.out.println("사용중인 메모리 : "+used_memory);
            json.put("used memory"+i,used_memory);
            Thread.sleep(100);

            totalUseMemory +=(used_memory / 8000000);

            Long memory_limit = Long.parseLong(memory_stats.get("limit").toString());

            Double memory_percent = (double)(used_memory) / memory_limit * 100.0;
            String m_percent = String.format("%.3f",memory_percent);
            System.out.println("Memory 점유률 : "+m_percent+"%");
            json.put("memory usage"+i,m_percent);
            Thread.sleep(100);

            //네트워크 : tx , tx 패킷 개수 , rx , rx 패킷 개수
            // 축적값이 아니기 때문에 전값 유지
            Map<String,Object> networks = (Map<String, Object>) stats.get("networks");

            /*
            * networks 를 key로 하는 json 정보에 대해 ...
            * 명칭이 docker engine 사이트에서느 eth0 , eth5 이던데 원인을 찾지 못했습니다.
            * 제 컴퓨터의 도커 컨테이너들은 전부 eth0 , eth1 입니다
            * 그래서 아래와 같이 코드를 구성하였기 때문에 확인시 실행이 되지 않는다면 eth5로 변경하여 실행 부탁드립니다.
            */

            Map<String,Object> eth0 = (Map<String, Object>) networks.get("eth0");
            Map<String,Object> eth1 = (Map<String, Object>) networks.get("eth1");

            int rxb0 = Integer.parseInt(eth0.get("rx_bytes").toString()) + Integer.parseInt(eth1.get("rx_bytes").toString());
            int rxp0 = Integer.parseInt(eth0.get("rx_packets").toString()) + Integer.parseInt(eth1.get("rx_packets").toString());
            int txb0 = Integer.parseInt(eth0.get("tx_bytes").toString()) + Integer.parseInt(eth1.get("tx_bytes").toString());
            int txp0 = Integer.parseInt(eth0.get("tx_packets").toString()) + Integer.parseInt(eth1.get("tx_packets").toString());

            if(packet_arr[i]==null)
            {
                System.out.println("-----모니터링 후 첫 출력이므로 중간에 실행 시 누적값 출력-----\n ");
                packet_arr[i]= new RxTx(rxb0,rxp0,txb0,txp0);
                System.out.println(packet_arr[i].toString());
                json.put("network"+i,packet_arr[i].toString());
            }
            else
            {
                int rxb1 = rxb0 - packet_arr[i].RxByte;
                int rxp1 = rxp0 - packet_arr[i].RxPacket;
                int txb1 = txb0 - packet_arr[i].TxByte;
                int txp1 = txp0 - packet_arr[i].TxPacket;

                packet_arr[i] = new RxTx(rxb0,rxp0,txb0,txp0);
                String packet_result = new RxTx(rxb1,rxp1,txb1,txp1).toString();
                System.out.println(packet_result);
                json.put("network"+i,packet_arr[i].toString());
            }
            Thread.sleep(100);

            i++;
        }
        return CpuUse;
    }
}
