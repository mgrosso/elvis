
    alter table bruce.NODE_CLUSTER 
        drop constraint FKFFDC89D88E4F00C;

    alter table bruce.NODE_CLUSTER 
        drop constraint node_id_fk;

    alter table bruce.YF_CLUSTER 
        drop constraint master_node_id_fk;

    drop table bruce.NODE_CLUSTER;

    drop table bruce.YF_CLUSTER;

    drop table bruce.YF_NODE;

    drop sequence bruce.hibernate_sequence;

    create table bruce.NODE_CLUSTER (
        node_id int8 not null,
        cluster_id int8 not null,
        primary key (node_id, cluster_id)
    );

    create table bruce.YF_CLUSTER (
        id int8 not null,
        name varchar(255),
        master_node_id int8 unique,
        primary key (id)
    );

    create table bruce.YF_NODE (
        id int8 not null,
        available bool,
        includeTable varchar(255),
        name varchar(255) not null,
        uri varchar(255) not null,
        primary key (id)
    );

    alter table bruce.NODE_CLUSTER 
        add constraint FKFFDC89D88E4F00C 
        foreign key (cluster_id) 
        references bruce.YF_CLUSTER;

    alter table bruce.NODE_CLUSTER 
        add constraint node_id_fk 
        foreign key (node_id) 
        references bruce.YF_NODE;

    create index yf_cluster_name_idx on bruce.YF_CLUSTER (name);

    alter table bruce.YF_CLUSTER 
        add constraint master_node_id_fk 
        foreign key (master_node_id) 
        references bruce.YF_NODE;

    create sequence bruce.hibernate_sequence;
